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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.disp

  (:require [czlab.loki.xpis :as loki]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.loki.net.core :as nc])

  (:import [java.io Closeable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> EventSubr
  c/Receivable
  (receive [me evt]
    (when (= (:type me)
             (:type evt))
      (c/debug "[%s]: recv'ed msg: %s" (:id me) (nc/pretty-event evt))
      (c/send (:session me) evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro esubr<>

  ""
  [session]

  `(c/object<> czlab.loki.net.disp.EventSubr
               {:session ~session
                :type loki/type-public
                :id (c/x->kw "subr#" (czlab.basal.util/seqint2)) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> Dispatcher
                 c/AtomicGS
                 (getf [me n] (get @(:o me) n))
                 (setf [me n v]
                       (swap! (:o me) assoc n v))
                 Closeable
                 (close [me]
                        (doseq [[_ c]
                                (.getf me :handlers)] (cas/close! c))
                        (.setf me :handlers {}))
                 loki/PubSub
                (unsub-if-session [me session]
                                  (doseq [[su _] (.getf me :handlers)
                                          :let [s (:session su)]
                                          :when (u/obj-eq? su s)]
                                    (loki/unsub me su)))
                (unsub [me handler]
                       (when-some [c (get (.getf me :handlers) handler)]
                         (cas/close! c)
                         (swap! (:o me) update-in [:handlers] dissoc handler)))
                (sub [me handler]
                     (let [c (cas/chan 4)]
                       (swap! (:o me) update-in [:handlers] assoc handler c)
                       (cas/go-loop []
                                    (when-some [msg (cas/<! c)]
                                      (if (= (:type handler)
                                             (:type msg))
                                        ;;cant type hint inside async code
                                        (c/receive handler msg))
                                      (recur)))))
                (pub-event [me msg]
                           (c/debug "pub msg = %s" (:code msg))
                           (doseq [[_ c] (.getf me :handlers)]
                             (cas/go (cas/>! c msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn edispatcher<>

  ""
  []

  (c/atomic<> Dispatcher
              {:handlers {}
               :id (czlab.basal.core/x->kw "disp#"
                                           (czlab.basal.util/seqint2)) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

