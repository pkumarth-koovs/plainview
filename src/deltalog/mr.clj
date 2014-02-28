(ns deltalog.mr
  (:require [deltalog.schema :as schema])
  (:import [org.apache.hadoop.fs Path FileSystem]
           [org.apache.hadoop.conf Configuration]
           [cascading.tap SinkMode]
           [cascading.tap.hadoop Hfs Lfs]
           [cascading.avro AvroScheme]
           [cascading.scheme.hadoop TextDelimited TextLine]
           [cascading.tuple Fields]
           [cascading.operation.expression ExpressionFilter]
           [cascading.pipe Pipe Each GroupBy Every]
           [cascading.operation.aggregator Last] ; for learning
           [cascading.flow Flow FlowDef]
           [cascading.flow.hadoop HadoopFlowConnector]
           [cascading.json.operation JSONSplitter JSONFlatten]))

(defn make-fields [fields] (Fields. (into-array fields)))

(defn make-splitter
  "Creates a JSON parser that will extract each field from the input
  JSON object. The resultant fields will have the same names as they do
  in the json object."
  [json-paths]
  (JSONSplitter. (make-fields json-paths) (into-array json-paths)))

; This creates a tap that will read a text file line-by-line.
; The resultant field will be named "line".
(def in-tap (Hfs. (AvroScheme.) "example/raw/raw-372.avro"))
(def out-tap (Hfs. (AvroScheme. schema/user-schema) "example/output" true))

(defn filter-ts-if
  "If timestamp evaluates to true, then this will create a new
  pipe that filters where the 'timestamp' field is greater than or equal
  to the given timestamp value. Otherwise it returns pipe."
  [pipe timestamp]
  (if timestamp
    (Each. pipe
           (make-fields ["timestamp"])
           (ExpressionFilter. (str "timestamp >= " timestamp) Long/TYPE))
    pipe))

(defn -main
  [& args]
  (let [copy-pipe (Pipe. "copy")
        #_(tail-pipe (-> splitter-pipe
                          (filter-ts-if (first args))
                          (GroupBy. (make-fields ["id"])
                                    (make-fields ["timestamp"]))
                          (Every. Fields/ALL (Last.) Fields/RESULTS)))

        flow-def (-> (FlowDef/flowDef)
                     (.addSource copy-pipe in-tap)
                     (.addTailSink copy-pipe out-tap))]

    (let [flow (.connect (HadoopFlowConnector.) flow-def)]
      (doto flow
        (.writeDOT "example/flow.dot")
        (.complete)))))
