(ns stream2es.stream.queue
  (:require [cheshire.core :as json]
            [clojure.java.io :as jio]
            [workroom.core :as work]
            [stream2es.util.io :as io]
            [stream2es.log :as log]
            [stream2es.stream :refer [new Stream
                                      Streamable CommandLine
                                      StreamStorage]]))

(defrecord QueueStream [])

(defrecord QueueStreamRunner [runner])

(defmethod new 'queue [_]
  (QueueStream.))

(extend-type QueueStream
  CommandLine
  (specs [_]
    [["-b" "--bulk-bytes" "Bulk size in bytes"
      :default (* 1024 100)
      :parse-fn #(Integer/parseInt %)]
     ["-q" "--queue-size" "Size of the internal bulk queue"
      :default 40
      :parse-fn #(Integer/parseInt %)]
     ["-i" "--index" "ES index" :default "foo"]
     ["-t" "--type" "ES type" :default "t"]
     ["--stream-buffer" "Buffer up to this many docs"
      :default 100
      :parse-fn #(Integer/parseInt %)]
     ["--broker" "Broker url"]
     ["--exchange" "Broker exchange"]
     ["--queue" "Broker queue"]])
  Stream
  (make-runner [_ opts handler]
    (QueueStreamRunner.
     (fn []
       (let [q (work/->Queue (:broker opts) (:exchange opts) (:queue opts))]
         (log/log 'consume-poll (:broker opts) (:exchange opts) (:queue opts))
         (work/consume-poll q (fn [msg]
                                (doall
                                 (map handler
                                      (line-seq
                                       (io/gz-reader
                                        (-> msg :_source :source)))))))))))
  StreamStorage
  (settings [_]
    {:number_of_shards 2
     :number_of_replicas 0})
  (mappings [_ type]
    {(keyword type)
     {:_all {:enabled false}
      :properties {}}}))
