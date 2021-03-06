package coprocessor;

import client.HQueueConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.MiniBatchOperationInProgress;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Created by zhuifeng on 2017/5/23.
 */
public class HbaseCoprocessor extends BaseRegionObserver {
    private static final Log LOG = LogFactory.getLog(HbaseCoprocessor.class);
    private long timestamp = 0;
    private short sequenceId = 0;

    @Override
    public void preBatchMutate(ObserverContext<RegionCoprocessorEnvironment> c, MiniBatchOperationInProgress<Mutation> miniBatchOp) throws IOException {
        //use preBatchMutate instead of prePut for this lock
        synchronized(this){
            long currentTime = EnvironmentEdgeManager.currentTime();
            LOG.info("timestamp is:"+timestamp);
            LOG.info("currentTime is:"+currentTime);
            LOG.info("sequenceId is:"+sequenceId);
            if(timestamp != currentTime){
                timestamp = currentTime;
                sequenceId = 0;
            }
            for(int i = 0; i < miniBatchOp.size(); ++i){
                if(miniBatchOp.getOperationStatus(i).getOperationStatusCode() != HConstants.OperationStatusCode.NOT_RUN){
                    continue;
                }
                Mutation mutation = miniBatchOp.getOperation(i);
                if(mutation instanceof Put){
                    if (sequenceId == Short.MAX_VALUE) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        timestamp = EnvironmentEdgeManager.currentTime();
                        sequenceId = 0;
                    }
                    tranformPutMessageId((Put)mutation, timestamp, sequenceId++);
                }
            }
        }
    }

    private void tranformPutMessageId(Put put, long timestamp, short sequenceId){
        LOG.info("transform timestamp is:"+timestamp);
        LOG.info("transform sequenceId is:"+sequenceId);
        System.arraycopy(Bytes.toBytes(timestamp), 0, put.getRow(),
                HQueueConstants.PARTITION_ID_LENGTH, HQueueConstants.TIMESTAMP_LENGTH);
        System.arraycopy(Bytes.toBytes(sequenceId), 0, put.getRow(),
                HQueueConstants.PARTITION_ID_LENGTH + HQueueConstants.TIMESTAMP_LENGTH,
                HQueueConstants.SEQUENCE_ID_LENGTH);
        for(List<Cell> cells : put.getFamilyCellMap().values()){
            for(Cell cell : cells){
                System.arraycopy(Bytes.toBytes(timestamp), 0, cell.getRowArray(),
                        cell.getRowOffset() + HQueueConstants.PARTITION_ID_LENGTH, HQueueConstants.TIMESTAMP_LENGTH);
                System.arraycopy(Bytes.toBytes(sequenceId), 0, cell.getRowArray(),
                        cell.getRowOffset() + HQueueConstants.PARTITION_ID_LENGTH + HQueueConstants.TIMESTAMP_LENGTH,
                        HQueueConstants.SEQUENCE_ID_LENGTH);
            }
        }
    }
}
