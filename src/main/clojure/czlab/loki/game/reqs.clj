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

  (:require [czlab.basal.resources :as r :refer [rstr]]
            [czlab.loki.xpis :as loki]
            [czlab.basal.log :as log]
            [czlab.basal.format :as f]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s]
            [czlab.basal.io :as i]
            [czlab.wabbit.xpis :as xp]
            [czlab.loki.player :as p]
            [czlab.loki.net.core :as nc]
            [czlab.loki.game.room :as gr]
            [czlab.loki.game.core :as gc])

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
                    xp/get-server c/id?? I18N/bundle)]
    (if (s/hgl? (s/sname gameid))
      (let
        [gameid (keyword gameid)
         g (gc/lookupGame gameid)
         p (if (some? g)
             (p/lookupPlayer principal
                             (c/charsit credential)))
         s (if (and (some? g)
                    (some? p)) (gr/openRoom g p evt))]
        (log/debug "game[%s] loaded as: %s" gameid g)
        (cond
          (nil? g)
          (c/do->nil
            (nc/replyError socket
                           ::loki/game-nok
                           (r/rstr rcb "game.notok")))
          (nil? p)
          (c/do->nil
            (nc/replyError socket
                           ::loki/user-nok
                           (r/rstr rcb "login.error")))
          (nil? s)
          (c/do->nil
            (nc/replyError socket
                           ::loki/rooms-full
                           (r/rstr rcb "room.none")))
          :else s))
      (c/do->nil
        (nc/replyError socket
                       ::loki/playreq-nok
                       (r/rstr rcb "bad.req"))))))

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
                    xp/get-server c/id?? I18N/bundle)]
    (if (and (s/hgl? (s/sname gameid))
             (s/hgl? (s/sname roomid)))
      (let
        [p (p/lookupPlayer principal credential)
         gameid (keyword gameid)
         roomid (keyword roomid)
         pss (some-> p
                     (gr/joinRoom  gameid roomid evt))]
        (cond
          (nil? p)
          (c/do->nil
            (nc/replyError socket
                           ::loki/user-nok
                           (r/rstr rcb "login.error")))
          (nil? pss)
          (c/do->nil
            (nc/replyError socket
                           ::loki/room-nok
                           (r/rstr rcb "room.bad")))
          :else pss))
      (c/do->nil
        (nc/replyError socket
                       ::loki/joinreq-nok
                       (r/rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

