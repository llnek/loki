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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.core

  (:require [czlab.loki.xpis :as loki]
            [clojure.java.io :as io]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.nettio.core :as cc]
            [czlab.loki.util :as u]
            [czlab.loki.net.core :as nc]
            [czlab.loki.game.reqs :as rs])

  (:import [java.io File]
           [czlab.basal XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lokiHandler

  ""
  [evt]

  (let [s (.strit ^XData (:body evt))
        p (:source evt)
        ch (:socket evt)
        {:keys [type code] :as req}
        (nc/decode-event s {:socket ch
                            :source p})]
    (cond
      (and (nc/is-private? req)
           (nc/is-code? loki/playgame-req req))
      (rs/do-playreq req)

      (and (nc/is-private? req)
           (nc/is-code? loki/joingame-req req))
      (rs/do-joinreq req)

      :t
      (let [{:keys [room session]}
            (cc/akey?? ch (cc/akey* u/RMSN))]
        (if-not (and session room)
          (c/error "no session attached to socket")
          (->> (assoc req :context session) (c/receive room)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


