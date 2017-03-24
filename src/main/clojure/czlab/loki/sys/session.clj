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

  (:use [czlab.basal.process]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.net.disp])

  (:import [czlab.loki.sys Room Player Session]
           [io.netty.channel Channel]
           [czlab.jasal Hierarchial]
           [czlab.loki.net Events]
           [czlab.loki.net MsgSender]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn session<>
  ""
  ^Session
  [^Room room ^Player plyr pnumber settingsArg]
  (let [impl (muble<> {:status false
                       :shutting? false})
        created (now<>)
        sid (str "session#" (seqint2))]
    (reify Session

      (parent [_] (.getv impl :parent))
      (setParent [_ _])

      (number [_] pnumber)
      (player [_] plyr)
      (room [_] room)

      (send [this msg]
        (when (and (not (.isShuttingDown this))
                   (.isConnected this))
          (-> ^MsgSender
              (.getv impl :tcp)
              (.send msg))))

      (receive [this evt]
        (trap! Exception "Unexpected onmsg called in Session."))
        ;;(log/debug "player session " sid " , onmsg called: " evt))

      (isShuttingDown [_] (bool! (.getv impl :shutting?)))
      (isConnected [this] (bool! (.getv impl :status)))

      (settings [_] settingsArg)

      (bind [this options]
        (.setv impl :tcp (tcpSender<> (:socket options)))
        (.setv impl :parent (:source options))
        (.setv impl :status true))

      (id [_] sid)

      (close [this]
        (when (.isConnected this)
          (.setv impl :shutting? true)
          (closeQ (.getv impl :tcp))
          (.unsetv impl :tcp)
          (.setv impl :status false)
          (.setv impl :shutting? false)))

      Object

      (hashCode [_] (.hashCode sid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Session obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

