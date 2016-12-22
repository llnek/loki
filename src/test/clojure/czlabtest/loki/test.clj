;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns

  czlabtest.loki.test

  (:require [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.loki.event.core]
        [clojure.test])

  (:import [io.netty.handler.codec.http.websocketx
            WebSocketFrame
            TextWebSocketFrame]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  EVT_JSON
  "{\"type\" : 100, \"code\": 200, \"body\": { \"a\" : 911 }}")
(def ^:private EVT_BODY {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (is (let [^TextWebSocketFrame
            evt (encodeEvent {:type 100 :code 200 :body EVT_BODY})
            s (.text evt)]
        (and (.indexOf s "100") > 0
             (.indexOf s "200"))))

  (is (let [evt (decodeEvent EVT_JSON {:x 3})]
        (and (== 100 (:type evt))
             (== 200 (:code evt))
             (== 911 (get-in evt [:body :a])))))

  (is (let [evt (reifyEvent 100 200 EVT_BODY {:x 7})]
        (and (== 100 (:type evt))
             (== 200 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))




  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.loki.czlabtestloki-test)

