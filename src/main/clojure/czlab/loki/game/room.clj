;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.room

  (:require [czlab.basal.logging :as log])

  (:use [czlab.convoy.nettio.core]
        [czlab.basal.format]
        [flatland.ordered.map]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.game.arena])

  (:import [czlab.loki.sys Room Player Session]
           [czlab.loki.game Arena Info]
           [czlab.wabbit.ctl Pluglet]
           [io.netty.channel Channel]
           [clojure.lang Keyword]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _room_mutex_ (Object.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; {game-id -> map
;;             { room-id -> room }}
(def ^:private free-rooms (atom (ordered-map)))
(def ^:private game-rooms (atom (ordered-map)))
(def ^:private vacancy 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countFreeRooms
  "" ^long [gameid] (count (get @free-rooms (keyword gameid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  "" ^long [gameid] (count (get @game-rooms (keyword gameid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! free-rooms assoc (keyword gameid) (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! game-rooms assoc (keyword gameid) (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^Room [gameid roomid]

  (locking _room_mutex_
    (let [gameid (keyword gameid)
          gm (@game-rooms gameid)
          r (get gm roomid)]
      (when (some? r)
        (log/debug "remove room(A): %s, game: %s" roomid gameid)
        (swap! game-rooms update-in [gameid] dissoc roomid)
        r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  ^Room [gameid roomid]

  (locking _room_mutex_
    (let [gameid (keyword gameid)
          gm (@free-rooms gameid)
          r (get gm roomid)]
      (when (some? r)
        (log/debug "remove room(F): %s, game: %s" roomid gameid)
        (swap! free-rooms update-in [gameid] dissoc roomid)
        r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addXXXRoom
  "" [^Arena room db dbt] {:pre [(some? room)]}

  (locking _room_mutex_
    (let [gid (.. room game id)
          rid (.id room)
          m? (contains? @db gid)]
      (log/debug "adding a %s: %s, game: %s" dbt rid gid)
      (if m?
        (swap! db update-in [gid] assoc rid room)
        (swap! db assoc gid (ordered-map rid room)))
      room)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addFreeRoom
  "Add a new partially filled room into the pending set"
  ^Room [^Arena room]
  (addXXXRoom room free-rooms "room(F)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addGameRoom
  "Move room into the active set"
  ^Room [^Arena room]
  (addXXXRoom room game-rooms "room(A)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom
  "" [gameid roomid] {:pre [(keyword? gameid)]}

  (log/debug "found a room(F): %s, game: %s" roomid gameid)
  (swap! free-rooms
         update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "Returns a free room which is detached from the pending set"
  {:tag Room}

  ([gameid roomid]
   (let [gameid (keyword gameid)
         gm (@free-rooms gameid)
         ^Room r (get gm roomid)]
     (when (some? r)
       (detachFreeRoom gameid (.id r))
       r)))

  ([gameid]
   (let [gameid (keyword gameid)
         gm (@free-rooms gameid)]
    (when-some
      [^Room r (first (vals gm))]
      (detachFreeRoom gameid (.id r))
      r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lookupGameRoom
  "" ^Room [gameid roomid]
  (log/debug "looking for room(A): %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- newFreeRoom
  "" ^Room [^Info game options]

  (let [p (partial removeGameRoom (.id game))
        room (arena<> game p options)]
    (log/debug "created a new room(F): %s" (.id room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" ^Session [^Info game ^Player plyr arg]

  (locking _room_mutex_
    (let [^Arena
          room (or (lookupFreeRoom (.id game))
                   (newFreeRoom game arg))
          s (get-in arg [:body :settings])
          ^Session
          pss (some-> room (.connect plyr s))]
      (when (some? room)
        (let
          [^Channel ch (:socket arg)
           src {:gameid (sname (.id game))
                :roomid (.id room)
                :puid (.id plyr)
                :pnum (.number pss)}
           evt (privateEvent<> Events/PLAYREQ_OK src)]
          (.bind pss arg)
          (setAKey ch PSSN pss)
          (log/debug "replying back to user: %s" evt)
          (replyEvent ch evt)
          (->> (publicEvent<> Events/PLAYER_JOINED
                              {:pnum (.number pss)
                               :puid (.id plyr)})
               (.broadcast room))
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open room))
            (addFreeRoom room))))
      pss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom
  "" ^Session
  [^Player plyr gameid roomid arg]
  {:pre [(some? plyr)]}

  (locking _room_mutex_
    (let [s (get-in arg [:body :settings])
          gameid (keyword gameid)
          ^Arena
          room (or (lookupGameRoom gameid roomid)
                   (lookupFreeRoom gameid roomid))
          ^Info
          game (some-> room .game)
          ch (:socket arg)]
      (when (and room
                 (< (.countPlayers room)
                    (.maxPlayers game)))
        (let [pss (.connect room plyr s)
              src {:gameid (sname (.id game))
                   :roomid (.id room)
                   :puid (.id plyr)
                   :pnum (.number pss)}
              evt (privateEvent<> Events/JOINREQ_OK src)]
          (.bind pss arg)
          (setAKey ch PSSN pss)
          (replyEvent ch evt)
          (log/debug "replying back to user: %s" evt)
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open room))
            (addFreeRoom room))
          pss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

