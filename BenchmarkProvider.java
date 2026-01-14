package com.example.resourcemapperapp;

import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BenchmarkProvider {
    
    public static class BenchmarkResults {
        public long randomGenTimeMs;
        public long matrixMultTimeMs;
        public long hashingTimeMs;
        public long singleThreadScore;
        public long multiThreadScore;
        public long overallScore;
        public String comparison;
    }
    
    private static final int RANDOM_ITERATIONS = 1000000;
    private static final int MATRIX_SIZE = 200;
    private static final int HASH_ITERATIONS = 50000;
    
    public BenchmarkResults runBenchmark() {
        BenchmarkResults results = new BenchmarkResults();
        
        // Run single-threaded benchmarks
        long startTime = System.currentTimeMillis();
        runRandomNumberTest();
        results.randomGenTimeMs = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        runMatrixMultiplication();
        results.matrixMultTimeMs = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        runHashingTest();
        results.hashingTimeMs = System.currentTimeMillis() - startTime;
        
        // Calculate single-thread score (lower time = higher score)
        // Normalize to a score out of 1000
        results.singleThreadScore = calculateSingleThreadScore(
            results.randomGenTimeMs,
            results.matrixMultTimeMs,
            results.hashingTimeMs
        );
        
        // Run multi-threaded benchmark
        startTime = System.currentTimeMillis();
        runMultiThreadedBenchmark();
        results.multiThreadScore = System.currentTimeMillis() - startTime;
        
        // Calculate overall score
        results.overallScore = calculateOverallScore(results.singleThreadScore, results.multiThreadScore);
        
        // Compare with standard phones
        results.comparison = compareWithStandardPhones(results.overallScore);
        
        return results;
    }
    
    private void runRandomNumberTest() {
        Random random = new Random();
        long sum = 0;
        for (int i = 0; i < RANDOM_ITERATIONS; i++) {
            sum += random.nextInt(1000);
        }
        // Use sum to prevent optimization
        if (sum < 0) {
            // This will never happen, but prevents compiler optimization
        }
    }
    
    private void runMatrixMultiplication() {
        int size = MATRIX_SIZE;
        double[][] a = new double[size][size];
        double[][] b = new double[size][size];
        double[][] c = new double[size][size];
        
        Random random = new Random();
        // Initialize matrices
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i][j] = random.nextDouble();
                b[i][j] = random.nextDouble();
            }
        }
        
        // Matrix multiplication
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += a[i][k] * b[k][j];
                }
                c[i][j] = sum;
            }
        }
    }
    
    private void runHashingTest() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = new byte[1024];
            Random random = new Random();
            
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                random.nextBytes(data);
                md.update(data);
                md.digest();
            }
        } catch (Exception e) {
            // Fallback to simple hash
            for (int i = 0; i < HASH_ITERATIONS; i++) {
                String test = "test" + i;
                test.hashCode();
            }
        }
    }
    
    private void runMultiThreadedBenchmark() {
        int numCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numCores);
        CountDownLatch latch = new CountDownLatch(numCores);
        
        for (int i = 0; i < numCores; i++) {
            executor.submit(() -> {
                // Run a mix of operations on each thread
                runRandomNumberTest();
                runHashingTest();
                latch.countDown();
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }
    }
    
    private long calculateSingleThreadScore(long randomTime, long matrixTime, long hashTime) {
        // Normalize times to a score (lower time = higher score)
        // Base scores: random ~50ms, matrix ~500ms, hash ~100ms for mid-range phone
        long normalizedRandom = Math.max(1, 5000 / Math.max(1, randomTime));
        long normalizedMatrix = Math.max(1, 50000 / Math.max(1, matrixTime));
        long normalizedHash = Math.max(1, 10000 / Math.max(1, hashTime));
        
        // Weighted average
        return (normalizedRandom * 2 + normalizedMatrix * 5 + normalizedHash * 3) / 10;
    }
    
    private long calculateOverallScore(long singleThreadScore, long multiThreadTime) {
        // Multi-thread score (lower time = higher score)
        // Expected multi-thread time for mid-range: ~200ms
        long multiThreadScore = Math.max(1, 20000 / Math.max(1, multiThreadTime));
        
        // Combine single-thread (70%) and multi-thread (30%) performance
        return (singleThreadScore * 7 + multiThreadScore * 3) / 10;
    }
    
    private String compareWithStandardPhones(long score) {
        if (score >= 800) {
            return "Flagship (iPhone 15 Pro, Galaxy S24 Ultra)";
        } else if (score >= 600) {
            return "High-end (Pixel 8, OnePlus 12)";
        } else if (score >= 400) {
            return "Mid-range (Pixel 7a, Galaxy A54)";
        } else if (score >= 250) {
            return "Budget (Galaxy A14, Redmi Note 12)";
        } else {
            return "Entry-level (Basic Android phones)";
        }
    }
}





