;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.player

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.session])

  (:import [czlab.jasal Idable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; player-db
;; {player-id -> {:p player :s {id -> session}}}
(def ^:private player-db (atom {}))
;; map of nicknames
(def ^:private userid-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-object LokiPlayer)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- player<> "" [userid passwd]
  (object<> LokiPlayer
            {:userid userid
             :passwd passwd
             :id (toKW "player#" (seqint2)) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createPlayer
  ""
  [^String userid ^chars passwd]
  {:pre [(hgl? userid)]}
  (locking userid-db
    (if (notin? @userid-db userid)
      (let [p (player<> userid passwd)
            pid (id?? p)]
        (swap! player-db assoc pid p)
        (swap! userid-db assoc userid pid))))
  (@player-db (@userid-db userid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removePlayer "" [userid]
  (locking userid-db
    (when-some [pid (@userid-db userid)]
      (swap! userid-db dissoc userid)
      (swap! player-db dissoc pid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupPlayer ""
  ([userid pwd] (createPlayer userid pwd))
  ([userid] (-> (@userid-db userid) (@player-db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn logout "" [player]
  (removeSessions player)
  (removePlayer (:userid player)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

