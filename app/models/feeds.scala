package models

import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue

// Feeds protocol
case class MetricsFeed(out: Enumerator[JsValue])