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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.core

  (:require [czlab.loki.xpis :as loki]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.niou.core :as nc]
            [czlab.basal.util :as u])

  (:import [clojure.lang APersistentMap]
           [czlab.jasal DataError Sendable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defmacro is-private? "" [evt] `(= czlab.loki.xpis/type-private (:type ~evt)))
(defmacro is-public? "" [evt] `(= czlab.loki.xpis/type-public (:type ~evt)))
(defmacro is-code? "" [c evt] `(= ~c (:code ~evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pretty-event

  "Pretty print the event"
  ^String [evt]

  {:pre [(map? evt)]}

  (let [{:keys [type code status]}
        evt
        m {:type (loki/msg-types type)
           :code (loki/msg-codes code) }]
    (prn-str (dissoc (merge evt m)
                     :context :socket :session :source))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn encode-event

  "Turn data into a json string"
  ^String
  [{:keys [timestamp status
           type code body] :as evt}]

  {:pre [(number? type)(number? code)]}

  (let [msg {:type type
             :code code
             :body (or body {})
             :timestamp (or timestamp (u/system-time))}]
    (-> (if status
          (assoc msg :status status) msg) i/fmt->json)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn decode-event

  "Returns event with socket info attached.
  If error, catch and return it with an invalid type."
  {:tag APersistentMap}

  ([data]
   (decode-event data nil))

  ([data extras]
   (c/debug "decoding json: %s" data)
   (c/try!
     (let [{:keys [type code] :as evt}
           (i/read-json data "utf-8" keyword)]
       (cond
         (not (number? type))
         (u/throw-BadData "Event type info: %s" type)
         (not (number? code))
         (u/throw-BadData "Event code info: %s" code)
         :t
         (merge evt {:type type :code code} extras))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn errorObj<>

  ""
  {:tag APersistentMap}

  ([etype ecode body]
   (errorObj<> etype ecode body nil))

  ([etype ecode]
   (errorObj<> etype ecode nil nil))

  ([etype ecode body arg]

   {:pre [(some? etype)
          (some? ecode)]}

   (let [body (if (string? body) {:message body} body)
         obj {:status loki/status-error
              :timestamp (u/system-time)
              :type etype
              :code ecode
              :body (or body {})}]
     (cond
       (map? arg)
       (merge obj arg)
       (some? arg)
       (assoc obj :context arg) :t obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn eventObj<>

  ""
  {:tag APersistentMap}

  ([etype ecode body]
   (eventObj<> etype ecode body nil))

  ([etype ecode]
   (eventObj<> etype ecode nil nil))

  ([etype ecode body arg]
   {:pre [(some? etype)
          (some? ecode)
          (or (nil? body)
              (map? body))]}
   (let [obj {:status loki/status-ok
              :timestamp (u/system-time)
              :type etype
              :code ecode
              :body (or body {})}]
     (cond
       (map? arg)
       (merge obj arg)
       (some? arg)
       (assoc obj :context arg) :else obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn private-event<>

  ""

  ([code body]
   (private-event<> code body nil))

  ([code body arg]
   (eventObj<> loki/type-private code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn public-event<>

  ""

  ([code body]
   (public-event<> code body nil))

  ([code body arg]
   (eventObj<> loki/type-public code body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reply-event

  "Reply back a msg"
  [ch msg]

  (c/debug (str "reply back a msg "
                "type: %s, code: %s")
           (loki/msg-types (:type msg))
           (loki/msg-codes (:code msg)))
  (c/do->nil
    (some-> ch
            (nc/reply-result (nc/ws-msg<> {:is-text? true
                                           :socket ch
                                           :body (encode-event msg)})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reply-error

  "Reply back an error"
  [ch error msg]

  (reply-event ch (errorObj<> loki/type-private error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn is-quit?

  ""
  [evt]

  (and (is-private? evt)
       (is-code? loki/quit evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn isMove?

  ""
  [evt]

  (and (is-private? evt)
       (is-code? loki/play-move evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pokeWait!

  ""

  ([room body]
   (pokeWait! room body nil))

  ([room body arg]
   (c/send room
          (private-event<> loki/poke-wait body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pokeMove!

  ""

  ([room body]
   (pokeMove! room body nil))

  ([room body arg]
   (c/send room
           (private-event<> loki/poke-move body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn syncArena!

  ""

  ([room body]
   (syncArena! room body nil))

  ([room body arg]
   (->>
     (if arg
       (private-event<> loki/sync-arena body arg)
       (public-event<> loki/sync-arena body))
     (c/send room ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pub-event

  ""
  [disp msg]

  (c/debug "pub msg = %s" (:code msg))
  (doseq [[_ c] (:handlers @disp)] (cas/go (cas/>! c msg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn bcast!

  ""

  ([room code body]
   (bcast! room code body nil))

  ([room code body arg]
   (loki/broad-cast room (public-event<> code body arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn stop!

  ""

  ([room body]
   (stop! room body nil))

  ([room body arg]
   (bcast! room loki/stop body arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

