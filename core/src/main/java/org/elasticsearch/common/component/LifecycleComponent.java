/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.component;

import org.elasticsearch.common.lease.Releasable;

/**
 * Releasable类是ElasticSearch自己封装JDK1.7中的AutoCloseable
 *
 * 该接口主要标记了Component的生命周期状态相关，当然还有添加和移除生命周期监听器.
 *
 * 主要还是看LifecycleComponent的实现类是怎么实现这些标记的逻辑的
 */
public interface LifecycleComponent extends Releasable {

    Lifecycle.State lifecycleState();

    void addLifecycleListener(LifecycleListener listener);

    void removeLifecycleListener(LifecycleListener listener);

    void start();

    void stop();
}
