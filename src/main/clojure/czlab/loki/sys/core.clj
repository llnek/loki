;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.sys.core

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.game.reqs])

  (:import [java.io File]
           [czlab.jasal XData Receivable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiHandler "" [evt]
  (let [s (.strit ^XData (:body evt))
        p (get-pluglet evt)
        ch (:socket evt)
        {:keys [type code] :as req}
        (decodeEvent s {:socket ch
                        :source p})]
    (cond
      (and (isPrivate? req)
           (isCode? Events/PLAYGAME_REQ req))
      (doPlayReq req)

      (and (isPrivate? req)
           (isCode? Events/JOINGAME_REQ req))
      (doJoinReq req)

      :else
      (if-some [a (getAKey ch RMSN)]
        (->> (assoc req :context (:session a))
             (. ^Receivable (:room a) receive))
        (log/error "no session attached to socket")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


