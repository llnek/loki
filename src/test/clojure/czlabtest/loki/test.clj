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
            TextWebSocketFrame]
           [czlab.loki.event Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  EVT_JSON
  "{\"type\" : 100, \"status\": 200, \"code\":111, \"body\": { \"a\" : 911 }}")
(def ^:private EVT_BODY {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (is (let [evt (eventObj<> 100 111 EVT_BODY {:x 7})]
        (and (== 100 (:type evt))
             (== Events/OK (:status evt))
             (== 111 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (errorObj<> 100 111 EVT_BODY {:x 7})]
        (and (== 100 (:type evt))
             (== Events/ERROR (:status evt))
             (== 111 (:code evt))
             (map? (:error evt))
             (== 7 (:x evt)))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111 EVT_BODY)
                    (encodeEvent ))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111)
                    (encodeEvent))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [evt (decodeEvent EVT_JSON {:x 3})]
        (and (== 100 (:type evt))
             (== 200 (:status evt))
             (== 111 (:code evt))
             (== 911 (get-in evt [:body :a])))))


  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.loki.czlabtestloki-test)

