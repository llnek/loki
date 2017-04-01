;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.sys.session

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.loki.sys.util]
        [czlab.loki.net.core])

  (:import [czlab.jasal Openable Sendable Idable]
           [io.netty.channel Channel]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private sessions-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Session
  Object
  (toString [me] (sname (id?? me)))
  Openable
  (open [_ options]
    (do->nil
      (swap! data
             assoc
             :status true
             :src (:source options)
             :tcp  (:socket options))))
  (close [_]
    (closeQ (:tcp @data))
    (swap! data assoc :status false :tcp nil))
  Idable
  (id [_] (:id @data))
  Sendable
  (send [_ msg]
    (let [{:keys [tcp shutting? status]} @data]
      (if (and status
               (not shutting?))
        (some-> ^Channel
                tcp
                (.writeAndFlush (encodeEvent msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn defsession "" [room player settings]
  (let [sid (toKW "sess#" (seqint2))
        pid (id?? player)
        s (entity<> Session
                    (merge settings
                           {:shutting? false
                            :created (now<>)
                            :status false
                            :src nil
                            :tcp nil
                            :id sid
                            :room room
                            :player player}))]
    (doto->> s
             (swap! sessions-db
                    update-in
                    [pid]
                    assoc (id?? s) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearAllSessions "" []
  (locking sessions-db
    (reset! sessions-db {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countSessions "" [player]
  (if player
    (count (@sessions-db (id?? player))) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeSession
  "Remove session from player" [session]
  {:pre [(some? session)]}
  (let [p (:player @session)]
    (swap! sessions-db
           update-in
           [(id?? p)]
           dissoc
           (id?? session))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addSession
  "Add a session to this player" [session]
  {:pre [(some? session)]}
  (let [p (:player @session)]
    (swap! sessions-db
           update-in
           [(id?? p)]
           assoc (id?? session) session)
    s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeSessions
  "Remove all sessions from player"
  [player]
  {:pre [(some? player)]}

  (let [pid (id?? player)]
    (when-some+ [m (@sessions-db pid)]
      (swap! sessions-db dissoc pid)
      (doseq [[_ c] m] (closeQ c)))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

