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
  "" [gameid] (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  "" [gameid] (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! free-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! game-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^Room [gameid roomid]

  (locking _room_mutex_
    (let [gm (@game-rooms gameid)
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
    (let [gm (@free-rooms gameid)
          r (get gm roomid)]
      (when (some? r)
        (log/debug "remove room(F): %s, game: %s" roomid gameid)
        (swap! free-rooms update-in [gameid] dissoc roomid)
        r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addFreeRoom
  "Add a new partially filled room
   into the pending set"
  ^Room [^Arena room] {:pre [(some? room)]}

  (locking _room_mutex_
    (let [rid (.id room)
          gid (.. room game id)
          m (@free-rooms gid)]
      (log/debug "adding a room(F): %s, game: %s" rid gid)
      (if (some? m)
        (swap! free-rooms update-in [gid] assoc rid room)
        (swap! free-rooms assoc gid (ordered-map rid room)))
      room)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addGameRoom
  "Move room into the active set"
  ^Room [^Arena room] {:pre [(some? room)]}

  (let [rid (.id room)
        gid (.. room game id)
        m (@game-rooms gid)]
    (log/debug "add a room(A): %s, game: %s" rid gid)
    (if (some? m)
      (swap! game-rooms update-in [gid] assoc rid room)
      (swap! game-rooms assoc gid (ordered-map rid room)))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom
  "" [gameid roomid]

  (log/debug "found a room(F): %s, game: %s" roomid gameid)
  (swap! free-rooms
         update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "Returns a free room which is detached from the pending set"
  {:tag Room}

  ([gameid roomid]
   (let [gm (@free-rooms gameid)
         ^Room r (get gm roomid)]
     (when (some? r)
       (detachFreeRoom gameid (.id r))
       r)))

  ([gameid]
   (let [gm (@free-rooms gameid)]
    (when-some
      [^Room r (first (vals gm))]
      (detachFreeRoom gameid (.id r))
      r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lookupGameRoom
  "" ^Room [gameid roomid]
  (log/debug "looking for room: %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- newFreeRoom
  "" ^Room [^Info game options]

  (let [room (arena<> game options)]
    (log/debug "created a new play room: %s" (.id room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" ^Session [^Info game ^Player plyr arg]

  (locking _room_mutex_
    (let [^Arena
          room (or (lookupFreeRoom (.id game))
                   (newFreeRoom game arg))
          ^Session
          pss (some-> room (.connect plyr))]
      (when (some? room)
        (let
          [^Channel ch (:socket arg)
           src {:puid (.id plyr)
                :room (.id room)
                :game (.id game)
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
              (addGameRoom room)
              (log/debug "room.canOpen = true")
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
    (let [^Arena
          room (or (lookupGameRoom gameid roomid)
                   (lookupFreeRoom gameid roomid))
          ^Info
          game (some-> room .game)
          ch (:socket arg)]
      (when (and room
                 (< (.countPlayers room)
                    (.maxPlayers game)))
        (let [pss (.connect room plyr)
              src {:puid (.id plyr)
                   :room (.id room)
                   :game (.id game)
                   :pnum (.number pss)}
              evt (privateEvent<> Events/JOINREQ_OK src)]
          (.bind pss arg)
          (setAKey ch PSSN pss)
          (replyEvent ch evt)
          (log/debug "replying back to user: %s" evt)
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (addGameRoom room)
              (log/debug "room.canOpen = true")
              (.open room))
            (addFreeRoom room))
          pss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

