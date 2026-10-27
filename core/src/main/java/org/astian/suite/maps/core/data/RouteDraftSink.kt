package org.astian.suite.maps.core.data

import org.astian.suite.maps.core.model.Route
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Put in the context of a planning `directions` call to receive the open router's routes the
 * moment they arrive, marked [Route.draft], while the fetch goes on to Google's traffic and the
 * road-name checks (SPEC 4.2, First look). Called at most once, from the fetch's own thread;
 * the call's return value is still the answer.
 */
class RouteDraftSink(val publish: (List<Route>) -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RouteDraftSink>
}
