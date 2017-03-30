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

  (:import [czlab.jasal Sendable Idable]
           [io.netty.channel Channel]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private sessions-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Session
  Object
  (hashCode [me] (.hashCode (id?? me)))
  (toString [me] (id?? me))
  (equals [me obj] (objEQ? me obj))
  Closeable
  (close [_]
    (closeQ (:tcp @data))
    (swap! data assoc :status false :tcp nil))
  Idable
  (id [_] (:id @data))
  (connect [_ options]
    (swap! data
           assoc
           :status true
           :parent (:source options)
           :tcp  (:socket options)))
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
  (let [sid (str "sess#" (seqint2))
        s (entity<> Session
                    (merge settings
                           {:shutting? false
                            :status false
                            :parent nil
                            :tcp nil
                            :created (now<>)
                            :id sid
                            :room room
                            :player player}))]
    (doto->> s
             (swap! sessions-db assoc (id?? s) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addSession
  "Add a session to this player" [player session]
  (swap! @sessions-db
         update-in
         [(id?? player)]
         assoc
         (id?? session) session))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn closeAll
  "Close all sessions for this player" [player]

  (when-some+ [m (@sessions-db (id?? player))]
    (swap! @sessions-db
           dissoc (id?? player))
    (doseq [[_ c] m] (closeQ c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

