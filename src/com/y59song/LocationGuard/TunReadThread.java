/*
 * Thread for handling and dispatching all ip packets (only tcp and udp)
 * Copyright (C) 2014  Yihang Song

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.y59song.LocationGuard;

import com.y59song.Forwader.ForwarderPools;
import com.y59song.Network.IP.IPDatagram;
import com.y59song.Utilities.ByteOperations;
import com.y59song.Utilities.MyLogger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by y59song on 06/06/14.
 */
public class TunReadThread extends Thread {
  private final FileInputStream localIn;
  private final FileChannel localInChannel;
  private final int limit = 2048;
  //private final ArrayDeque<IPDatagram> readQueue = new ArrayDeque<IPDatagram>();
  private ConcurrentLinkedQueue<IPDatagram> readQueue = new ConcurrentLinkedQueue<IPDatagram>();
  private final ForwarderPools forwarderPools;
  private final Dispatcher dispatcher;

  public TunReadThread(FileDescriptor fd, MyVpnService vpnService) {
    localIn = new FileInputStream(fd);
    localInChannel = localIn.getChannel();
    this.forwarderPools = vpnService.getForwarderPools();
    dispatcher = new Dispatcher();
  }

  public void run() {
    try {
      ByteBuffer packet = ByteBuffer.allocate(limit);
      IPDatagram ip;
      dispatcher.start();
      while (!isInterrupted()) {
        packet.clear();
        if (localInChannel.read(packet) > 0) {
          packet.flip();
          if ((ip = IPDatagram.create(packet)) != null) {
            //MyLogger.debugInfo("TunReadThread", ByteOperations.byteArrayToString(ip.payLoad().data()));
            /*
            synchronized(readQueue) {
              readQueue.addLast(ip);
              readQueue.notify();
            }
            */
            readQueue.offer(ip);
          }
        } else {
          // length = 0 is possible, -1 means reach the end of the stream
          Thread.sleep(1);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    clean();
  }

  private class Dispatcher extends Thread {
    int total = 0;
    public void run() {
      IPDatagram temp;
      while(!isInterrupted()) {
        /*
        synchronized (readQueue) {
          while ((temp = readQueue.pollFirst()) == null) {
            try {
              readQueue.wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
        */
        while((temp = readQueue.poll()) == null) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        int port = temp.payLoad().getSrcPort();
        forwarderPools.get(port, temp.header().protocol()).forwardRequest(temp);
      }
    }
  }

  private void clean() {
    dispatcher.interrupt();
    try {
      localIn.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
