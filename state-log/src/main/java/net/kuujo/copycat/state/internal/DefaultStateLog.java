/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.state.internal;

import net.kuujo.copycat.CopycatException;
import net.kuujo.copycat.protocol.Consistency;
import net.kuujo.copycat.resource.internal.AbstractResource;
import net.kuujo.copycat.resource.internal.ResourceContext;
import net.kuujo.copycat.state.StateLog;
import net.kuujo.copycat.state.StateLogConfig;
import net.kuujo.copycat.util.concurrent.Futures;
import net.kuujo.copycat.util.internal.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default state log partition implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@SuppressWarnings("rawtypes")
public class DefaultStateLog<T> extends AbstractResource<StateLog<T>> implements StateLog<T> {
  private static final int SNAPSHOT_ENTRY = 0;
  private static final int COMMAND_ENTRY = 1;
  private final Map<Integer, OperationInfo> operations = new ConcurrentHashMap<>(128);
  private final Consistency defaultConsistency;
  private final SnapshottableLogManager log;
  private Supplier snapshotter;
  private Consumer installer;

  public DefaultStateLog(ResourceContext context) {
    super(context);
    this.log = (SnapshottableLogManager) context.log();
    defaultConsistency = context.config()
      .<StateLogConfig>getResourceConfig()
      .getDefaultConsistency();
    context.consumer(this::consume);
  }

  @Override
  public <U extends T, V> StateLog<T> registerCommand(String name, Function<U, V> command) {
    Assert.state(isClosed(), "Cannot register command on open state log");
    operations.put(name.hashCode(), new OperationInfo<>(command, false));
    return this;
  }

  @Override
  public StateLog<T> unregisterCommand(String name) {
    Assert.state(isClosed(), "Cannot unregister command on open state log");
    operations.remove(name.hashCode());
    return this;
  }

  @Override
  public <U extends T, V> StateLog<T> registerQuery(String name, Function<U, V> query) {
    Assert.state(isClosed(), "Cannot register command on open state log");
    Assert.isNotNull(name, "name");
    Assert.isNotNull(query, "query");
    operations.put(name.hashCode(), new OperationInfo<>(query, true, defaultConsistency));
    return this;
  }

  @Override
  public <U extends T, V> StateLog<T> registerQuery(String name, Function<U, V> query, Consistency consistency) {
    Assert.state(isClosed(), "Cannot register command on open state log");
    Assert.isNotNull(name, "name");
    Assert.isNotNull(query, "query");
    operations.put(name.hashCode(), new OperationInfo<>(query, true, consistency == null || consistency == Consistency.DEFAULT ? defaultConsistency : consistency));
    return this;
  }

  @Override
  public StateLog<T> unregisterQuery(String name) {
    Assert.state(isClosed(), "Cannot unregister command on open state log");
    operations.remove(name.hashCode());
    return this;
  }

  @Override
  public StateLog<T> unregister(String name) {
    Assert.state(isClosed(), "Cannot unregister command on open state log");
    operations.remove(name.hashCode());
    return this;
  }

  @Override
  public <V> StateLog<T> snapshotWith(Supplier<V> snapshotter) {
    Assert.state(isClosed(), "Cannot modify state log once opened");
    this.snapshotter = snapshotter;
    return this;
  }

  @Override
  public <V> StateLog<T> installWith(Consumer<V> installer) {
    Assert.state(isClosed(), "Cannot modify state log once opened");
    this.installer = installer;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <U> CompletableFuture<U> submit(String command, T entry) {
    Assert.state(isOpen(), "State log not open");
    OperationInfo<T, U> operationInfo = operations.get(command.hashCode());
    if (operationInfo == null) {
      return Futures.exceptionalFutureAsync(new CopycatException(String.format("Invalid state log command %s", command)), executor);
    }

    // If this is a read-only command, check if the command is consistent. For consistent operations,
    // queries are forwarded to the current cluster leader for evaluation. Otherwise, it's safe to
    // read stale data from the local node.
    ByteBuffer buffer = serializer.writeObject(entry);
    ByteBuffer commandEntry = ByteBuffer.allocate(8 + buffer.capacity());
    commandEntry.putInt(COMMAND_ENTRY); // Entry type
    commandEntry.putInt(command.hashCode());
    commandEntry.put(buffer);
    commandEntry.rewind();
    if (operationInfo.readOnly) {
      return context.query(commandEntry, operationInfo.consistency).thenApplyAsync(serializer::readObject, executor);
    } else {
      return context.commit(commandEntry).thenApplyAsync(serializer::readObject, executor);
    }
  }

  @Override
  public synchronized CompletableFuture<StateLog<T>> open() {
    return runStartupTasks()
      .thenComposeAsync(v -> context.open(), executor)
      .thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> close() {
    return context.close()
      .thenComposeAsync(v -> runShutdownTasks(), executor);
  }

  /**
   * Consumes a log entry.
   *
   * @param index The entry index.
   * @param entry The log entry.
   * @return The entry output.
   */
  @SuppressWarnings({"unchecked"})
  private ByteBuffer consume(Long index, ByteBuffer entry) {
    int entryType = entry.getInt();
    switch (entryType) {
      case SNAPSHOT_ENTRY: // Snapshot entry
        installSnapshot(entry.slice());
        return ByteBuffer.allocate(0);
      case COMMAND_ENTRY: // Command entry
        int commandCode = entry.getInt();
        OperationInfo operationInfo = operations.get(commandCode);
        if (operationInfo != null) {
          T value = serializer.readObject(entry.slice());
          return serializer.writeObject(operationInfo.execute(index, value));
        }
        throw new IllegalStateException("Invalid state log operation");
      default:
        throw new IllegalArgumentException("Invalid entry type");
    }
  }

  /**
   * Checks whether to take a snapshot.
   */
  private void checkSnapshot(long index) {
    // If the given index is the last index of a lot segment and the segment is not the last segment in the log
    // then the index is considered snapshottable.
    if (log.isSnapshottable(index)) {
      takeSnapshot(index);
    }
  }

  /**
   * Takes a snapshot and compacts the log.
   */
  private void takeSnapshot(long index) {
    Object snapshot = snapshotter != null ? snapshotter.get() : null;
    ByteBuffer snapshotBuffer = serializer.writeObject(snapshot);
    snapshotBuffer.flip();
    ByteBuffer snapshotEntry = ByteBuffer.allocate(snapshotBuffer.limit() + 4);
    snapshotEntry.putInt(SNAPSHOT_ENTRY);
    snapshotEntry.put(snapshotBuffer);
    snapshotEntry.flip();
    try {
      log.appendSnapshot(index, snapshotEntry);
    } catch (IOException e) {
      throw new CopycatException("Failed to compact state log", e);
    }
  }

  /**
   * Installs a snapshot.
   */
  @SuppressWarnings("unchecked")
  private void installSnapshot(ByteBuffer snapshot) {
    if (installer != null) {
      Object value = serializer.readObject(snapshot);
      installer.accept(value);
    }
  }

  @Override
  public String toString() {
    return String.format("%s[name=%s]", getClass().getSimpleName(), context.name());
  }

  /**
   * State command info.
   */
  private class OperationInfo<TT, U> {
    private final Function<TT, U> function;
    private final boolean readOnly;
    private final Consistency consistency;

    private OperationInfo(Function<TT, U> function, boolean readOnly) {
      this(function, readOnly, Consistency.DEFAULT);
    }

    private OperationInfo(Function<TT, U> function, boolean readOnly, Consistency consistency) {
      this.function = function;
      this.readOnly = readOnly;
      this.consistency = consistency;
    }

    private U execute(Long index, TT entry) {
      if (index != null)
        checkSnapshot(index);
      return function.apply(entry);
    }
  }

}
