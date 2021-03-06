/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.reactivesocket;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Actions;
import rx.schedulers.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HystrixDashboardStreamTest extends HystrixStreamTest {

    HystrixDashboardStream stream;

    @Before
    public void init() {
        stream = HystrixDashboardStream.getNonSingletonInstanceOnlyUsedInUnitTests(10);
    }

    @Test
    public void testStreamHasData() throws Exception {
        final AtomicBoolean commandShowsUp = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        for (int i = 0; i < 2; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.observe();
        }

        stream.observe().take(NUM).subscribe(dashboardData -> {
                    System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Received data with : " + dashboardData.commandMetrics.size() + " commands");
                    for (HystrixCommandMetrics metrics: dashboardData.commandMetrics) {
                        if (metrics.getCommandKey().name().equals("SyntheticBlockingCommand")) {
                            commandShowsUp.set(true);
                        }
                    }
                },
                Actions.empty(),
                () -> {
                    System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnCompleted");
                    latch.countDown();
                });

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(commandShowsUp.get());
    }

    @Test
    public void testTwoSubscribersOneUnsubscribes() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger payloads1 = new AtomicInteger(0);
        AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(latch1::countDown)
                .subscribe(new Subscriber<HystrixDashboardStream.DashboardData>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(HystrixDashboardStream.DashboardData dashboardData) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + dashboardData);
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(latch2::countDown)
                .subscribe(new Subscriber<HystrixDashboardStream.DashboardData>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(HystrixDashboardStream.DashboardData dashboardData) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + dashboardData);
                        payloads2.incrementAndGet();
                    }
                });
        //execute 1 command, then unsubscribe from first stream. then execute the rest
        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            if (i == 1) {
                s1.unsubscribe();
            }
        }

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
        assertTrue("s1 got less data than s2", payloads2.get() > payloads1.get());
    }

    @Test
    public void testTwoSubscribersBothUnsubscribe() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger payloads1 = new AtomicInteger(0);
        AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .observe()
                .take(10)
                .doOnUnsubscribe(latch1::countDown)
                .subscribe(new Subscriber<HystrixDashboardStream.DashboardData>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(HystrixDashboardStream.DashboardData dashboardData) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + dashboardData);
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .observe()
                .take(10)
                .doOnUnsubscribe(latch2::countDown)
                .subscribe(new Subscriber<HystrixDashboardStream.DashboardData>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(HystrixDashboardStream.DashboardData dashboardData) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + dashboardData);
                        payloads2.incrementAndGet();
                    }
                });
        //execute half the commands, then unsubscribe from both streams, then execute the rest
        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            if (i == 25) {
                s1.unsubscribe();
                s2.unsubscribe();
            }
        }
        assertFalse(stream.isSourceCurrentlySubscribed());  //both subscriptions have been cancelled - source should be too

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
    }

    @Test
    public void testTwoSubscribersOneSlowOneFast() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean foundError = new AtomicBoolean(false);

        Observable<HystrixDashboardStream.DashboardData> fast = stream
                .observe()
                .observeOn(Schedulers.newThread());
        Observable<HystrixDashboardStream.DashboardData> slow = stream
                .observe()
                .observeOn(Schedulers.newThread())
                .map(n -> {
                    try {
                        System.out.println("Sleeping on thread : " + Thread.currentThread().getName());
                        Thread.sleep(100);
                        return n;
                    } catch (InterruptedException ex) {
                        return n;
                    }
                });

        Observable<Boolean> checkZippedEqual = Observable.zip(fast, slow, (payload, payload2) -> payload == payload2);

        Subscription s1 = checkZippedEqual
                .take(10000)
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnError : " + e);
                        e.printStackTrace();
                        foundError.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + b);
                    }
                });

        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertFalse(foundError.get());
    }
}