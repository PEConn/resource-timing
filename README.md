# Resource Timing API Demo

This Android application demonstrates how to use the [Web Resource Timing API](https://developer.mozilla.org/en-US/docs/Web/API/Performance_API/Resource_timing) to track which page resources are loaded from the local cache and which are loaded from the network.

https://github.com/user-attachments/assets/d204b4ff-cfde-42ff-b22f-5419aece6727

Information about individual resources is printed to the Android logs, you can view it with `adb logcat | grep ResourceTiming`.

## Resource Timing API

This API provides detailed network timing data about the application's resources.
We attach a [PerformanceObserver](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceObserver) to track performance events and read the [deliveryType](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceResourceTiming/deliveryType) and [transferSize](https://developer.mozilla.org/en-US/docs/Web/API/PerformanceResourceTiming/transferSize) properties to determine whether the resource was fetched from the network, a valid cache, or a revalidated cache.

- **Valid Cache Hit:** A resource that is loaded completely from the cache without needing to contact the server. This is identified when `deliveryType == "cache"` and `transferSize == 0`.
- **Revalidated Cache Hit:** A resource that is loaded from the cache, but required contacting the server to verify it is still fresh (e.g., using `304 Not Modified`). This is identified when `deliveryType == "cache"` and `transferSize > 0`.
- **Network:** Resources loaded from the network normally.

### Cross origin limitations

Unfortunately this API does not provide timing information by default for cross-origin requests.
This can be enabled by adding a response header to the request - for more information [see here](https://developer.mozilla.org/en-US/docs/Web/API/Performance_API/Resource_timing#cross-origin_timing_information).

## WebView APIs

We use `WebViewCompat#addDocumentStartJavaScript` to inject some JavaScript that adds the `PerformanceObserver` and `WebViewCompat#addWebMessageListener` to provide a channel to pass the resource timing information back to the application.

## Code structure

All the interesting code lives in [MainActivity.kt](./app/src/main/java/dev/conn/resourcetimingdemo/MainActivity.kt).
