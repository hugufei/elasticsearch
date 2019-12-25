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

package org.elasticsearch.http;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.transport.BoundTransportAddress;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

// 发送请求逻辑分组率属于HttpServerTransport
// LifecycleComponent接口标记了生命周期状态相关的逻辑
public interface HttpServerTransport extends LifecycleComponent {

    String HTTP_SERVER_WORKER_THREAD_NAME_PREFIX = "http_server_worker";
    String HTTP_SERVER_BOSS_THREAD_NAME_PREFIX = "http_server_boss";

    BoundTransportAddress boundAddress();

    HttpInfo info();

    HttpStats stats();

    /**
     * Dispatches HTTP requests.
     */
    // 将dispatchRequest这样的方法设计成接口，而发送请求逻辑分组率属于HttpServerTransport
    // 所以将Dispatcher接口设计成HttpServerTransport的内部接口，这样所有的Dispatcher的实现类都会带有HttpServerTransport接口的标记
    interface Dispatcher {

        /**
         * Dispatches the {@link RestRequest} to the relevant request handler or responds to the given rest channel directly if
         * the request can't be handled by any request handler.
         *
         * @param request       the request to dispatch
         * @param channel       the response channel of this request
         * @param threadContext the thread context
         */
        // 发送request到相关处理请求程序，如果request不能被任何处理请求程序处理则直接响应给RestChannel
        void dispatchRequest(RestRequest request, RestChannel channel, ThreadContext threadContext);

        /**
         * Dispatches a bad request. For example, if a request is malformed it will be dispatched via this method with the cause of the bad
         * request.
         *
         * @param request       the request to dispatch
         * @param channel       the response channel of this request
         * @param threadContext the thread context
         * @param cause         the cause of the bad request
         */
        // 发送一个失败的RestRequest，用在request是残缺的情况下。
        void dispatchBadRequest(RestRequest request, RestChannel channel, ThreadContext threadContext, Throwable cause);

    }

}
