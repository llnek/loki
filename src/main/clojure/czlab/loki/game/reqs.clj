;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.reqs

  (:require [czlab.loki.xpis :as loki :refer :all]
            [czlab.basal.resources :refer [rstr]]
            [czlab.basal.logging :as log])

  (:use [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.wabbit.xpis]
        [czlab.loki.player]
        [czlab.loki.net.core]
        [czlab.loki.game.room]
        [czlab.loki.game.core])

  (:import [czlab.jasal I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn doPlayReq
  "source json = {:gameid, :principal, :credential}"
  [{:keys [source socket body] :as evt}]

  (let [{:keys [gameid principal credential]}
        body
        rcb (some-> source
                    get-server id?? I18N/bundle)]
    (if (hgl? (sname gameid))
      (let
        [gameid (keyword gameid)
         g (lookupGame gameid)
         p (if (some? g)
             (lookupPlayer principal
                           (charsit credential)))
         s (if (and (some? g)
                    (some? p)) (openRoom g p evt))]
        (log/debug "game[%s] loaded as: %s" gameid g)
        (cond
          (nil? g)
          (do->nil
            (replyError socket
                        ::loki/game-nok
                        (rstr rcb "game.notok")))
          (nil? p)
          (do->nil
            (replyError socket
                        ::loki/user-nok
                        (rstr rcb "login.error")))
          (nil? s)
          (do->nil
            (replyError socket
                        ::loki/rooms-full
                        (rstr rcb "room.none")))
          :else s))
      (do->nil
        (replyError socket
                    ::loki/playreq-nok
                    (rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request to join a specific game room.  Not used now.
;; source json = [gameid, roomid, userid, password]
(defn doJoinReq
  "Request to join a specific game room.  Not used now.
  source json = {:gameid, :roomid, :principal, :credential}"
  [{:keys [source socket body] :as evt}]

  (let [{:keys [gameid roomid principal credential]}
        body
        rcb (some-> source
                    get-server id?? I18N/bundle)]
    (if (and (hgl? (sname gameid))
             (hgl? (sname roomid)))
      (let
        [p (lookupPlayer principal credential)
         gameid (keyword gameid)
         roomid (keyword roomid)
         pss (some-> p
                     (joinRoom  gameid roomid evt))]
        (cond
          (nil? p)
          (do->nil
            (replyError socket
                        ::loki/user-nok
                        (rstr rcb "login.error")))
          (nil? pss)
          (do->nil
            (replyError socket
                        ::loki/room-nok
                        (rstr rcb "room.bad")))
          :else pss))
      (do->nil
        (replyError socket
                    ::loki/joinreq-nok
                    (rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

