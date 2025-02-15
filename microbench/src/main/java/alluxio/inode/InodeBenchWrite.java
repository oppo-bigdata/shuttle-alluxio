/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.inode;

import static alluxio.inode.InodeBenchBase.HEAP;
import static alluxio.inode.InodeBenchBase.ROCKS;
import static alluxio.inode.InodeBenchBase.ROCKSCACHE;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;

/**
 * This benchmark measures the time it takes to write inodes to
 * a tree like structure, this includes locking and traversing the
 * path.
 * The following parameters can be varied:
 * mDepth - the number of levels in the inode tree
 * mType - the type of inode storage to use
 * mRocksConfig - see {@link RocksBenchConfig}
 * **/
public class InodeBenchWrite {

  @State(Scope.Thread)
  public static class ThreadState {
    long[] mNxtFileId;
    int mMyId = 0;
    int mNxtDepth = 0;

    @Setup(Level.Iteration)
    public void setup(Db db, ThreadParams params) {
      mNxtFileId = new long[db.mDepth + 1];
      mNxtDepth = 0;
      mMyId = params.getThreadIndex();
    }

    @TearDown(Level.Iteration)
    public void after() {
      System.out.printf("Insert count for id %d: %s%n",
          mMyId, Arrays.toString(mNxtFileId));
    }
  }

  @State(Scope.Benchmark)
  public static class Db {

    @Param({HEAP, ROCKS, ROCKSCACHE})
    public String mType;

    @Param({RocksBenchConfig.JAVA_CONFIG, RocksBenchConfig.BASE_CONFIG,
        RocksBenchConfig.EMPTY_CONFIG, RocksBenchConfig.BLOOM_CONFIG})
    public String mRocksConfig;

    @Param({"0", "1", "10"})
    public int mDepth;

    InodeBenchBase mBase;

    @Setup(Level.Iteration)
    public void setup() throws Exception {
      mBase = new InodeBenchBase(mType, mRocksConfig);
      mBase.createBasePath(mDepth);
    }

    @TearDown(Level.Iteration)
    public void after() throws Exception {
      mBase.after();
      mBase = null;
    }
  }

  @Benchmark
  public void testMethod(Db db, ThreadState ts) throws Exception {
    db.mBase.writeFile(ts.mMyId, ts.mNxtDepth, ts.mNxtFileId[ts.mNxtDepth]);
    ts.mNxtFileId[ts.mNxtDepth]++;
    if (db.mDepth > 0) {
      ts.mNxtDepth = (ts.mNxtDepth + 1) % (db.mDepth + 1);
    }
  }

  public static void main(String []args) throws RunnerException {
    Options opt = new OptionsBuilder().include(InodeBenchWrite.class.getSimpleName())
        .forks(1).addProfiler(StackProfiler.class).build();
    new Runner(opt).run();
  }
}
