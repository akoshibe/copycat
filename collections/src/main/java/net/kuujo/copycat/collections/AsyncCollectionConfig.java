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
package net.kuujo.copycat.collections;

import com.typesafe.config.ConfigValueFactory;
import net.kuujo.copycat.protocol.Consistency;
import net.kuujo.copycat.resource.ResourceConfig;
import net.kuujo.copycat.util.internal.Assert;

import java.util.Map;

/**
 * Asynchronous collection configuration.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class AsyncCollectionConfig<T extends AsyncCollectionConfig<T>> extends ResourceConfig<T> {
  private static final String ASYNC_COLLECTION_CONSISTENCY = "consistency";

  private static final String CONFIGURATION = "collection";
  private static final String DEFAULT_CONFIGURATION = "collection-defaults";

  protected AsyncCollectionConfig(Map<String, Object> config, String... resources) {
    super(config, addResources(resources, CONFIGURATION, DEFAULT_CONFIGURATION));
  }

  protected AsyncCollectionConfig(String... resources) {
    super(addResources(resources, CONFIGURATION, DEFAULT_CONFIGURATION));
  }

  protected AsyncCollectionConfig(T config) {
    super(config);
  }

  /**
   * Sets the collection read consistency.
   *
   * @param consistency The collection read consistency.
   * @throws java.lang.NullPointerException If the consistency is {@code null}
   */
  public void setConsistency(String consistency) {
    this.config = config.withValue(ASYNC_COLLECTION_CONSISTENCY, ConfigValueFactory.fromAnyRef(Consistency.parse(Assert.isNotNull(consistency, "consistency")).toString()));
  }

  /**
   * Sets the collection read consistency.
   *
   * @param consistency The collection read consistency.
   * @throws java.lang.NullPointerException If the consistency is {@code null}
   */
  public void setConsistency(Consistency consistency) {
    this.config = config.withValue(ASYNC_COLLECTION_CONSISTENCY, ConfigValueFactory.fromAnyRef(Assert.isNotNull(consistency, "consistency").toString()));
  }

  /**
   * Returns the collection read consistency.
   *
   * @return The collection read consistency.
   */
  public Consistency getConsistency() {
    return Consistency.parse(config.getString(ASYNC_COLLECTION_CONSISTENCY));
  }

  /**
   * Sets the collection read consistency, returning the configuration for method chaining.
   *
   * @param consistency The collection read consistency.
   * @return The collection configuration.
   * @throws java.lang.NullPointerException If the consistency is {@code null}
   */
  @SuppressWarnings("unchecked")
  public T withConsistency(String consistency) {
    setConsistency(consistency);
    return (T) this;
  }

  /**
   * Sets the collection read consistency, returning the configuration for method chaining.
   *
   * @param consistency The collection read consistency.
   * @return The collection configuration.
   * @throws java.lang.NullPointerException If the consistency is {@code null}
   */
  @SuppressWarnings("unchecked")
  public T withConsistency(Consistency consistency) {
    setConsistency(consistency);
    return (T) this;
  }

}
