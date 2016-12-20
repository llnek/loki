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

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.odin.game.room

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.convoy.netty.core]
        [czlab.xlib.format]
        [flatland.ordered.map]
        [czlab.xlib.core]
        [czlab.xlib.guids]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.odin.system.util]
        [czlab.odin.event.core]
        [czlab.odin.event.disp]
        [czlab.odin.game.session])

  (:import [io.netty.handler.codec.http.websocketx TextWebSocketFrame]
           [czlab.wabbit.server Cljshim Container]
           [czlab.wabbit.io IoService]
           [io.netty.channel Channel]
           [czlab.xlib Dispatchable]
           [clojure.lang Keyword]
           [czlab.odin.core
            Game
            Room
            Player
            Session
            GameEngine]
           [czlab.odin.event
            Events
            EventSub
            PubSub
            Sender Receiver]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; {game-id -> map
;;             { room-id -> room }}
(def ^:private FREE-ROOMS (atom {}))
(def ^:private GAME-ROOMS (atom {}))
(def ^:private vacancy 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGame
  "Find a game from the registry"
  ^Game
  [gameid rego]
  (when-some [g (get rego gameid)]
    (let [{:keys [enabled? minp maxp engine]
           :or {enabled? false
                minp 1
                maxp 1
                engine ""}}
          (:network g)]
      (log/debug "found game with uuid = %s" gameid)
      (reify Game
        (supportMultiPlayers [_] (boolean enabled?))
        (maxPlayers [_] (if (spos? maxp)
                          (int maxp)
                          (int 9)))
        (minPlayers [_] (if (spos? minp)
                          (int minp)
                          (int 1)))
        (name [_] (:name g))
        (engineClass [_] engine)
        (gist [_] g)
        (id [_] (:uuid g))
        (unload [_] )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^Room
  [gameid roomid]
  (let [gm (@GAME-ROOMS gameid)
        r (get gm roomid)]
    (when (some? r)
      (log/debug "remove room(A): %s, game: %s" roomid gameid)
      (swap! GAME-ROOMS
             assoc
             gameid (dissoc gm roomid))
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  ^Room
  [gameid roomid]
  (let [gm (@FREE-ROOMS gameid)
        r (get gm roomid)]
    (log/debug "remove room(F): %s, game: %s" roomid gameid)
    (swap! FREE-ROOMS
           assoc
           gameid (dissoc gm roomid))
    r))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addFreeRoom
  "Add a new partially filled room
   into the pending set"
  ^Room
  [^Room room]
  {:pre [(some? room)]}
  (let [rid (.id room)
        g (.game room)
        gid (.id g)
        m (@FREE-ROOMS gid)]
    (log/debug "add a room(F): %s, game: %s" rid gid)
    (swap! FREE-ROOMS
           assoc
           gid
           (assoc m rid room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addGameRoom
  "Move room into the active set"
  ^Room
  [^Room room]
  {:pre [(some? room)]}
  (let [rid (.id room)
        g (.game room)
        gid (.id g)
        m (@GAME-ROOMS gid)]
    (log/debug "add a room(A): %s, game: %s" rid gid)
    (swap! GAME-ROOMS
           assoc
           gid
           (assoc m rid room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Returns a free room which is detached from the pending set.
(defmulti lookupFreeRoom "" {:tag Room} (fn [a & args] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod lookupFreeRoom
  Game
  [^Game game]
  (let [gid (.id game)
        gm (@FREE-ROOMS gid)]
    (when-some [r (first (vals gm))]
      (let [rid (.id ^Room r)]
        (log/debug "found a room(F): %s, game: %s" rid gid)
        (swap! FREE-ROOMS
               assoc
               gid
               (dissoc gm rid)))
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod lookupFreeRoom
  Keyword
  [gameid roomid]
  (let [gm (@FREE-ROOMS gameid)
        r (get gm roomid)]
    (when (some? r)
      (log/debug "found a room(F): %s, game: %s" roomid gameid)
      (swap! FREE-ROOMS
             assoc
             gameid
             (dissoc gm roomid))
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGameRoom
  ""
  ^Room
  [gameid roomid]
  (log/debug "looking for room: %s, game: %s" roomid gameid)
  (get (@GAME-ROOMS gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkLocalSubr
  ""
  ^EventSub
  [^Session ps]
  (reify EventSub

    (eventType [_] Events/LOCAL)
    (session [_] ps)

    Receiver
    (onMsg [_ evt] (.sendMsg ps evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onSessionMsg
  ""
  [^Room room evt]
  (if-some [s (:context evt)]
    (.sendMsg ^Sender s evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onLocalMsg
  ""
  [^Room room evt]
  ;;for now, just bcast everything
  (.broadcast room evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyPlayRoom
  ""
  ^Room
  [^Game gameObj {:keys [source]}]

  (let [ctr (.server ^IoService source)
        crt (.cljrt ctr)
        engObj (.callEx crt (.engineClass gameObj)
                            (object-array [(atom {}) (ref {})]))
        impl (muble<> {:shutting false})
        sessions (atom (ordered-map))
        disp (reifyDispatcher)
        rid (uuid<>)
        created (now<>)]
    (reify Room

      (countPlayers [_] (count @sessions))

      (disconnect [_ ps]
        (let [^Session ps ps
              py (.player ps)]
          (swap! sessions dissoc (.id ps))
          (.removeSession py ps)
          (.unsubscribeIfSession disp ps)))

      (connect [this p]
        (let [ps (reifyPlayerSession this p (seqint2))
              ^Player py p
              src {:puid (.id py)
                   :pnum (.number ps)}]
          (swap! sessions assoc (.id ps) ps)
          (.addSession py ps)
          ps))

      (isShuttingDown [_] (.getv impl :shutting))
      (isActive [_] (true? (.getv impl :active)))

      (canActivate [this]
        (and (not (.isActive this))
             (>= (.countPlayers this)
                 (.minPlayers gameObj))))

      (broadcast [_ evt] (.publish disp evt))

      (engine [_] engObj)

      (game [_] gameObj)

      (id [_] rid)

      (close [_]
        (doseq [^Session v (vals @sessions)]
          (-> (.player v)
              (.removeSession v))
          (closeQ v))
        (reset! sessions (ordered-map)))

      (activate [this]
        (let [^GameEngine eng (.engine this)
              sss (vals @sessions)]
          (log/debug "activating room %s" rid)
          (.setv impl :active true)
          (doseq [s sss]
            (.addHandler this (mkLocalSubr s)))
          (doto eng
            (.init  sss)
            (.ready  this))))

      (removeHandler [_ h] (.unsubscribe disp h))
      (addHandler [_ h] (.subscribe disp h))

      (sendMsg [this msg]
        (condp = (:type msg)
          Events/LOCAL (onLocalMsg this msg)
          Events/UNIT (onSessionMsg this msg)
          (log/warn "room.sendmsg: unhandled event %s" msg)))

      (onMsg [this evt]
        (let [^GameEngine eng (.engine this)]
          (log/debug "room got an event %s" evt)
          (condp = (:type evt)
            Events/LOCAL (onLocalMsg this evt)
            Events/UNIT (.update eng evt)
            (log/warn "room.onmsg: unhandled event %s" evt))))

      Object

      (hashCode [this] (.hashCode rid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Room obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn newFreeRoom
  ""
  ^Session
  [^Game game ^Player py options]
  (let [room (reifyPlayRoom game options)]
    (log/debug "created a new play room: %s" (.id room))
    (.connect room py)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  ""
  ^Session
  [^Game game ^Player plyr arg]

  (let [pss (some-> (lookupFreeRoom game)
                    (.connect plyr))
        ^Session
        pss (or pss
                (newFreeRoom game plyr arg))
        ^Room
        room (some-> pss (.room))]
    (when (some? room)
      (if (.canActivate room)
        (do
          (log/debug "room has enough players, turning active")
          (addGameRoom room))
        (addFreeRoom room))
      (let
        [^Channel ch (:socket arg)
         src {:room (.id room)
              :game (.id game)
              :pnum (.number pss)}
         evt (reifyUnitEvent Events/PLAYREQ_OK src)]
        (.bind pss arg)
        (setAKey ch PLAY_SESSION pss)
        (log/debug "replying back to user: %s" evt)
        (.writeAndFlush ch (encodeEvent evt))
        (->> (reifyLocalEvent Events/PLAYER_JOINED
                           {:pnum (.number pss)
                            :puid (.id plyr)})
             (.broadcast room))
        (when (.canActivate room)
          (log/debug "room.canActivate = true")
          (.activate room))))
    pss))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom
  ""
  ^Session
  [^Room room ^Player plyr arg]
  {:pre [(some? room)(some? plyr)]}
  (let [^Channel ch (:socket arg)
        game (.game room)]
    (when (<= (.countPlayers room)
              (.maxPlayers game))
      (let [pss (.connect room plyr)
            src {:room (.id room)
                 :game (.id game)
                 :pnum (.number pss)}
            evt (reifyUnitEvent Events/JOINREQ_OK src)]
        (.bind pss arg)
        (setAKey ch PLAY_SESSION pss)
        (.writeAndFlush ch (encodeEvent evt))
        (log/debug "replying back to user: %s" evt)
        (when-not (.isActive room)
          (if (.canActivate room)
            (do
              (log/debug "room.canActivate = true")
              (.activate room)
              (addGameRoom room))
            (addFreeRoom room)))
        pss))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

