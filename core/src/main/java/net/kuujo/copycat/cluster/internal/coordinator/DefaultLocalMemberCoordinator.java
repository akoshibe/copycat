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
package net.kuujo.copycat.cluster.internal.coordinator;

import net.kuujo.copycat.cluster.MessageHandler;
import net.kuujo.copycat.cluster.internal.MemberInfo;
import net.kuujo.copycat.protocol.Protocol;
import net.kuujo.copycat.protocol.ProtocolException;
import net.kuujo.copycat.protocol.ProtocolServer;
import net.kuujo.copycat.util.concurrent.Futures;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Default local member implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DefaultLocalMemberCoordinator extends AbstractMemberCoordinator implements LocalMemberCoordinator {
  private final ProtocolServer server;
  private final Executor executor;
  private final Map<Integer, Map<Integer, Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>>>> handlers = new ConcurrentHashMap<>();

  public DefaultLocalMemberCoordinator(MemberInfo info, Protocol protocol, Executor executor) {
    super(info);
    try {
      URI realUri = new URI(info.uri());
      if (!protocol.isValidUri(realUri)) {
        throw new ProtocolException(String.format("Invalid protocol URI %s", info.uri()));
      }
      this.server = protocol.createServer(realUri);
    } catch (URISyntaxException e) {
      throw new ProtocolException(e);
    }
    this.executor = executor;
  }

  @Override
  public CompletableFuture<ByteBuffer> send(String topic, int address, int id, ByteBuffer message) {
    Map<Integer, Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>>> topicHandlers = handlers.get(topic.hashCode());
    if (topicHandlers != null) {
      Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>> addressHandlers = topicHandlers.get(address);
      if (addressHandlers != null) {
        MessageHandler<ByteBuffer, ByteBuffer> handler = addressHandlers.get(id);
        if (handler != null) {
          CompletableFuture<ByteBuffer> future = new CompletableFuture<ByteBuffer>();
          executor.execute(() -> {
            handler.apply(message).whenComplete((result, error) -> {
              executor.execute(() -> {
                if (error == null) {
                  future.complete(result);
                } else {
                    future.completeExceptionally(error);
                }
              });
            });
          });
          return future;
        }
      }
    }
    return Futures.exceptionalFuture(new IllegalStateException("No handlers"));
  }

  @Override
  public  LocalMemberCoordinator register(String topic, int address, int id, MessageHandler<ByteBuffer, ByteBuffer> handler) {
    Map<Integer, Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>>> topicHandlers = handlers.computeIfAbsent(topic.hashCode(), t -> new ConcurrentHashMap<>());
    Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>> addressHandlers = topicHandlers.computeIfAbsent(address, a -> new ConcurrentHashMap<>());
    addressHandlers.put(id, handler);
    return this;
  }

  @Override
  public LocalMemberCoordinator unregister(String topic, int address, int id) {
    Map<Integer, Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>>> topicHandlers = handlers.get(topic.hashCode());
    if (topicHandlers != null) {
      Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>> addressHandlers = topicHandlers.get(address);
      if (addressHandlers != null) {
        addressHandlers.remove(id);
        if (addressHandlers.isEmpty()) {
          topicHandlers.remove(address);
          if (topicHandlers.isEmpty()) {
            handlers.remove(topic.hashCode());
          }
        }
      }
    }
    return this;
  }

  /**
   * Handles a request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed once the response is ready.
   */
  private CompletableFuture<ByteBuffer> handle(ByteBuffer request) {
    Map<Integer, Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>>> topicHandlers = handlers.get(request.getInt());
    if (topicHandlers != null) {
      Map<Integer, MessageHandler<ByteBuffer, ByteBuffer>> addressHandlers = topicHandlers.get(request.getInt());
      if (addressHandlers != null) {
        MessageHandler<ByteBuffer, ByteBuffer> handler = addressHandlers.get(request.getInt());
        if (handler != null) {
          return CompletableFuture.runAsync(() -> {}, executor).thenCompose(v -> handler.apply(request.slice()));
        }
      }
    }
    return Futures.exceptionalFuture(new IllegalStateException("No handlers"));
  }

  @Override
  public synchronized CompletableFuture<MemberCoordinator> open() {
    return super.open()
      .thenComposeAsync(v -> server.listen(), executor)
      .thenRun(() -> server.handler(this::handle))
      .thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> close() {
    return super.close()
      .thenComposeAsync(v -> server.close(), executor)
      .thenRun(() -> server.handler(null));
  }

  @Override
  public String toString() {
    return String.format("%s[uri=%s]", getClass().getCanonicalName(), uri());
  }

}
