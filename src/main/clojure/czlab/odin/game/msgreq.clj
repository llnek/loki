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

  czlab.odin.game.msgreq

  (:require [czlab.xlib.resources :refer [rstr]]
            [czlab.xlib.logging :as log])

  (:use [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.odin.event.core]
        [czlab.odin.game.room]
        [czlab.odin.game.player])

  (:import [io.netty.handler.codec.http.websocketx TextWebSocketFrame]
           [czlab.wabbit.io IoService WSockEvent]
           [czlab.xlib Muble I18N XData]
           [io.netty.channel Channel]
           [czlab.odin.event Events]
           [czlab.odin.core
            Game
            Room
            Player
            Session]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rError
  "Reply back an error"
  [^Channel ch error msg]
  (log/debug "replying back an error code: %s" error)
  (do->nil
    (->> (reifyUnitEvent error {:message (str msg)})
         (encodeEvent)
         (.writeAndFlush ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn doPlayReq
  "source json = {:gameid, :userid, :password}"
  ^Session
  [{:keys [^IoService source socket body] :as evt}]
  (let [rcb (I18N/bundle (.. source server id))
        {:keys [gameid userid password]} body]
    (if (hgl? gameid)
      (let
        [g (lookupGame gameid)
         p (if (some? g)
             (lookupPlayer userid
                           password))
         ps (if (and (some? g)
                     (some? p))
              (openRoom g p evt))
         r (some-> ps (.room))]
        (log/debug "gameid %s loaded as %s" gameid g)
        (cond
          (nil? g)
          (do->nil
            (rError socket
                    Events/GAME_NOK
                    (rstr rcb "game.notok")))
          (nil? p)
          (do->nil
            (rError socket
                    Events/USER_NOK
                    (rstr rcb "login.error")))
          (nil? r)
          (do->nil
            (rError socket
                    Events/ROOMS_FULL
                    (rstr rcb "room.none")))
          :else
          ps))
      (do->nil
        (rError socket
                Events/PLAYREQ_NOK
                (rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request to join a specific game room.  Not used now.
;; source json = [gameid, roomid, userid, password]
(defn doJoinReq
  "Request to join a specific game room.  Not used now.
  source json = {:gameid, :roomid, :userid, :password}"
  ^Session
  [{:keys [^IoService source socket body] :as evt}]
  (let [rcb (I18N/bundle (.. source server id))
        {:keys [gameid roomid userid password]} body]
    (if (and (hgl? gameid)
             (hgl? roomid))
      (let
        [p (lookupPlayer userid password)
         r (if (some? p)
             (or (lookupGameRoom gameid roomid)
                 (lookupFreeRoom gameid roomid)))]
        (cond
          (nil? p)
          (do->nil
            (rError socket
                    Events/USER_NOK
                    (rstr rcb "login.error")))
          (nil? r)
          (do->nil
            (rError socket
                    Events/ROOM_NOK
                    (rstr rcb "room.bad")))
          :else
          (let
            [pss (joinRoom r p evt)]
            (if (nil? pss)
              (rError socket
                      Events/ROOM_FILLED
                      (rstr rcb "room.full")))
            pss)))
      (do->nil
        (rError socket
                Events/JOINREQ_NOK
                (rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

