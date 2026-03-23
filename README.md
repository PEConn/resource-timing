# ResourceTiming API Demo

This minimal Android application demonstrates how to bridge the WebView and native Android code using powerful web APIs to track, categorize, and report how web resources are loaded (cache, network, or undetermined).

## High-Level Overview

There are two critical APIs supplied by `androidx.webkit` that allow this deep level of insight inside a WebView environment cleanly and securely:

### 1. Intercepting Resource Loads via `addDocumentStartJavaScript`
Historically, `WebView`'s native method for tracking resource loading meant relying on `WebViewClient.shouldInterceptRequest`. However, this was limited when trying to truly understand *how* an asset was fetched (e.g. from the cache vs the network).

Using `WebViewCompat.addDocumentStartJavaScript`, we can inject a JavaScript script into the page before any other scripts run. This allows us to set up a `PerformanceObserver` that observes `resource` timing entries as they happen.

The `PerformanceResourceTiming` API provides details like `transferSize` and `decodedBodySize`.
- If `transferSize === 0` but the content isn't empty (`decodedBodySize > 0`), the browser served the resource directly from its internal cache.
- If `transferSize > 0`, the load required a network round-trip.

### 2. Communicating Back to Native via `addWebMessageListener`
`WebViewCompat.addWebMessageListener` gives developers a highly performant, secure way to stream data back to native Kotlin/Java code across a specific messaging port instead of using the older, more complex `addJavascriptInterface`.

The injected JavaScript observer wraps the categorized resource size in a JSON string and uses the provided listener (`AndroidListener.postMessage(...)`) to broadcast it back to the Android layer.

The Kotlin application parses this JSON asynchronously and aggregates the values, displaying a real-time summary to the user at the bottom of the screen.

## Cross-Origin Limitations

A notable limitation of this approach stems from web security mechanisms built into the `PerformanceResourceTiming` API.

When resources are fetched from a different origin (e.g., using a third-party CDN or an external API) and that response does not include the `Timing-Allow-Origin` HTTP header, the browser intentionally obscures specific timing and sizing metrics to prevent cross-origin timing attacks.

In these cases:
- `transferSize` will report `0`.
- `decodedBodySize` will report `0`.
- `encodedBodySize` will report `0`.

Because these sizes are zeroed out by the browser, our JavaScript logic categorizes such resources as **Undetermined**. You will still know a resource was loaded, but you will not be able to gather cache vs network statistics or specific byte sizes for it. You must ensure `Timing-Allow-Origin: *` (or the specific origin) is present on third-party responses to gather full analytics.
