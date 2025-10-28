package com.scorbutics.maven.util;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FuzzyListMatcher {

    public static <T, U> Map<T, U> findAndRemoveMatches(
            final List<T> listA,
            final List<U> listB,
            final Function<T, String> stringifierA,
            final Function<U, String> stringifierB,
            final double similarityThreshold) {

        final Map<T, U> matches = new LinkedHashMap<>();
        final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

        final Iterator<T> iteratorA = listA.iterator();

        while (iteratorA.hasNext()) {
            final T itemA = iteratorA.next();
            final String stringA = stringifierA.apply(itemA);
            U bestMatch = null;
            double bestSimilarity = 0.0;

            // Find the best match in listB for current itemA
            for (final U itemB : listB) {
                final String stringB = stringifierB.apply(itemB);
                final double similarity = calculateSimilarity(stringA, stringB, levenshtein);

                if (similarity > bestSimilarity && similarity >= similarityThreshold) {
                    bestSimilarity = similarity;
                    bestMatch = itemB;
                    if (similarity == 1.0) {
                        break; // Perfect match found
                    }
                }
            }

			// If a match is found, record it and remove from the input list
            if (bestMatch != null) {
                matches.put(itemA, bestMatch);
                iteratorA.remove();  // Remove from listA only
            }
        }

        return matches;
    }

    private static double calculateSimilarity(final String s1, final String s2, final LevenshteinDistance levenshtein) {
        final int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        final int distance = levenshtein.apply(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

}