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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.session

  (:require [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.niou.core :as cc]
            [czlab.loki.net.core :as nc])

  (:import [java.io Closeable]
           [czlab.basal XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private sessions-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> GameSession
                 c/Openable
                 (open [me options]
                       (swap! me merge (merge {:status true}
                                              (select-keys options
                                                           [:source :socket]))) me)
                 Closeable
                 (close [me]
                        (i/klose (:socket @me))
                        (swap! me merge {:status false :socket nil}))
                 c/Idable
                 (id [me] (:id @me))
                 c/Sendable
                 (send [me msg]
                       (let [{:keys [socket shutting? status]} @me]
                         (if (and status
                                  (not shutting?))
                           (some-> socket
                                   (cc/reply-result (cc/ws-msg<> {:socket socket
                                                                  :is-text? true
                                                                  :body (XData. (nc/encode-event msg))})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn session<>

  "Create a session object linking a player to a game-room."
  [room player settings]

  (let [sid (c/x->kw "sess#" (u/seqint2))
        pid (:id player)
        s (atom (c/object<> GameSession (merge settings
                       {:roomid (:id room)
                        :shutting? false
                        :created (u/system-time)
                        :status false
                        :source nil
                        :socket nil
                        :id sid
                        :player player})))]
    (c/doto->> s
               (swap! sessions-db
                      update-in [pid] assoc (:id @s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clear-all-sessions

  ""
  []

  (reset! sessions-db {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn count-sessions

  ""
  [player]

  (if player
    (count (@sessions-db (:id player))) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn remove-session

  ""
  [session]

  {:pre [(some? session)]}

  (c/do->nil
    (if-some [p (:player @session)]
      (swap! sessions-db update-in [(:id p)] dissoc (:id @session)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn add-session

  ""
  [session]

  {:pre [(some? session)]}

  (if-some [p (:player @session)]
    (swap! sessions-db update-in [(:id p)] assoc (:id @session) session))
  session)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn remove-sessions

  ""
  [player]

  {:pre [(some? player)]}

  (c/do->nil
    (if-some [pid (:id player)]
      (let [m (@sessions-db pid)]
        (swap! sessions-db dissoc pid)
        (doseq [[_ c] m] (i/klose c))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

