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

import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.cluster.internal.coordinator.ClusterCoordinator;
import net.kuujo.copycat.cluster.internal.coordinator.CoordinatorConfig;
import net.kuujo.copycat.cluster.internal.coordinator.DefaultClusterCoordinator;
import net.kuujo.copycat.resource.Resource;

/**
 * Asynchronous multi-map.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 *
 * @param <K> The multimap key type.
 * @param <V> The multimap entry type.
 */
public interface AsyncMultiMap<K, V> extends AsyncMultiMapProxy<K, V>, Resource<AsyncMultiMap<K, V>> {

  /**
   * Creates a new asynchronous multimap with the default cluster configuration.<p>
   *
   * The multimap will be constructed with the default cluster configuration. The default cluster configuration
   * searches for two resources on the classpath - {@code cluster} and {cluster-defaults} - in that order. Configuration
   * options specified in {@code cluster.conf} will override those in {cluster-defaults.conf}.<p>
   *
   * Additionally, the multimap will be constructed with an multimap configuration that searches the classpath for
   * three configuration files - {@code {name}}, {@code multimap}, {@code multimap-defaults}, {@code resource}, and
   * {@code resource-defaults} - in that order. The first resource is a configuration resource with the same name
   * as the multimap resource. If the resource is namespaced - e.g. `multimaps.my-multimap.conf` - then resource
   * configurations will be loaded according to namespaces as well; for example, `multimaps.conf`.
   *
   * @param name The asynchronous multimap name.
   * @param uri The asynchronous multimap member URI.
   * @param <K> The multimap key type.
   * @param <V> The multimap value type.
   * @return The asynchronous multimap.
   */
  static <K, V> AsyncMultiMap<K, V> create(String name, String uri) {
    return create(name, uri, new ClusterConfig(), new AsyncMultiMapConfig());
  }

  /**
   * Creates a new asynchronous multimap.<p>
   *
   * The multimap will be constructed with an multimap configuration that searches the classpath for
   * three configuration files - {@code {name}}, {@code multimap}, {@code multimap-defaults}, {@code resource}, and
   * {@code resource-defaults} - in that order. The first resource is a configuration resource with the same name
   * as the multimap resource. If the resource is namespaced - e.g. `multimaps.my-multimap.conf` - then resource
   * configurations will be loaded according to namespaces as well; for example, `multimaps.conf`.
   *
   * @param name The asynchronous multimap name.
   * @param uri The asynchronous multimap member URI.
   * @param cluster The cluster configuration.
   * @param <K> The multimap key type.
   * @param <V> The multimap value type.
   * @return The asynchronous multimap.
   */
  static <K, V> AsyncMultiMap<K, V> create(String name, String uri, ClusterConfig cluster) {
    return create(name, uri, cluster, new AsyncMultiMapConfig());
  }

  /**
   * Creates a new asynchronous multimap.
   *
   * @param name The asynchronous multimap name.
   * @param uri The asynchronous multimap member URI.
   * @param cluster The cluster configuration.
   * @param config The multimap configuration.
   * @param <K> The multimap key type.
   * @param <V> The multimap value type.
   * @return The asynchronous multimap.
   */
  static <K, V> AsyncMultiMap<K, V> create(String name, String uri, ClusterConfig cluster, AsyncMultiMapConfig config) {
    ClusterCoordinator coordinator = new DefaultClusterCoordinator(uri, new CoordinatorConfig().withName(name).withClusterConfig(cluster));
    return coordinator.<AsyncMultiMap<K, V>>getResource(name, config.resolve(cluster))
      .addStartupTask(() -> coordinator.open().thenApply(v -> null))
      .addShutdownTask(coordinator::close);
  }

}
