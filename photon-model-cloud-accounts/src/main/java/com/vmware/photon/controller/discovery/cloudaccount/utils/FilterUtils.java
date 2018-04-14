/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.discovery.cloudaccount.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

/**
 * Query filter parsing utils.
 */
public class FilterUtils {
    public static final String DOT = ".";

    /**
     * Examines {@code providedQuery} (and its nested boolean clauses), looking for a query term
     * property name that matches anything in {@code matchNames}. If {@code providedQuery} contains
     * a match, then {@code handleMatch} is invoked, passing in the specific query object that
     * contained the match.
     *
     * @param providedQuery the input query object
     * @param matchNames investigate {@code providedQuery} looking for these property name terms
     * @param handleMatch invoke lambda, passing in matching {@code Query} object, if match found
     */
    public static void findPropertyNameMatch(Query providedQuery, Collection<String> matchNames, Consumer<Query> handleMatch) {
        if (providedQuery == null) {
            throw new IllegalArgumentException("'query' cannot be null");
        }
        if (matchNames == null) {
            throw new IllegalArgumentException("'propertyNames' cannot be null");
        }

        if (providedQuery.term != null && matchNames.contains(providedQuery.term.propertyName)) {
            handleMatch.accept(providedQuery);
        }

        if (providedQuery.booleanClauses == null || providedQuery.booleanClauses.isEmpty()) {
            return;
        }

        for (Query booleanClause : providedQuery.booleanClauses) {
            if (booleanClause.term != null && matchNames.contains(booleanClause.term.propertyName)) {
                handleMatch.accept(booleanClause);
            }

            if (booleanClause.booleanClauses != null) {
                findPropertyNameMatch(booleanClause, matchNames, handleMatch);
            }
        }
    }

    /**
     * Get match value for given term from the query.
     *
     * @param query The query
     * @param term The term for which the match value is to be extracted
     * @return The match value
     */
    public static String getMatchValue(Query query, String term) {
        if (query == null) {
            throw new IllegalArgumentException("'query' cannot be null");
        }

        if (query.term != null && query.term.propertyName.equals(term)) {
            return query.term.matchValue;
        }

        if (query.booleanClauses == null || query.booleanClauses.isEmpty()) {
            return null;
        }

        for (Query booleanClause : query.booleanClauses) {
            if (booleanClause.term != null && term.equals(booleanClause.term.propertyName)) {
                return booleanClause.term.matchValue;
            }

            if (booleanClause.booleanClauses != null) {
                String matchValue = getMatchValue(booleanClause, term);
                if (matchValue != null) {
                    return matchValue;
                }
            }
        }

        return null;
    }

    /**
     * Get set of values for given term from the in-clause query.
     *
     * @param query The query
     * @param term The term for which the match value is to be extracted
     * @return The match values
     */
    public static Set<String> getMatchValues(Query query, String term) {
        if (query == null) {
            throw new IllegalArgumentException("'query' cannot be null");
        }

        if (query.booleanClauses == null || query.booleanClauses.isEmpty()) {
            return null;
        }

        for (Query booleanClause : query.booleanClauses) {
            // In case of a single value xenon optimize it to a field clause.
            if (booleanClause.term != null && booleanClause.term.propertyName.equals(term)) {
                return Collections.singleton(booleanClause.term.matchValue);
            }

            if (booleanClause.term != null || booleanClause.booleanClauses == null
                    || booleanClause.booleanClauses.isEmpty()) {
                continue;
            }

            Set<String> matchValues = new HashSet<>();
            for (Query clause : booleanClause.booleanClauses) {
                // Check if all the property names in the boolean clause matches the term
                if (clause.term == null || !clause.term.propertyName.equals(term)) {
                    matchValues = null;
                    break;
                }

                matchValues.add(clause.term.matchValue);
            }

            if (matchValues != null && !matchValues.isEmpty()) {
                return matchValues;
            }

            matchValues = getMatchValues(booleanClause, term);
            if (matchValues != null) {
                return matchValues;
            }
        }

        return null;
    }

    /**
     * Examines {@code providedQuery} (and its nested boolean clauses), looking for a query term
     * property name that matches anything in {@code matchNames}. If {@code providedQuery} contains
     * a match,then the match counter is incremented.
     *
     * @param providedQuery the input query object
     * @param matchNames investigate {@code providedQuery} looking for these property name terms
     */
    public static boolean doesQueryMatchGivenFilters(Query providedQuery,
            Collection<String> matchNames) {
        final AtomicInteger matchesFound = new AtomicInteger(0);
        findPropertyNameMatch(providedQuery, matchNames, query -> {
            matchesFound.incrementAndGet();
        });
        return matchesFound.get() > 0 ? true : false;
    }

    /**
     * Examines {@code providedQuery} (and its nested boolean clauses), looking for a query term
     * property name that matches anything in {@code matchNames}. If {@code providedQuery} contains
     * a match and has a SHOULD_OCCUR clause then it is added to a set of clauses that have an OR
     * condition between them.
     *
     * @param providedQuery the input query object
     * @param matchNames investigate {@code providedQuery} looking for these property name terms
     */
    public static void extractORClausesBasedOnGivenFilters(Query providedQuery,
            Collection<String> matchNames, Set<Query> orClauses) {
        findPropertyNameMatch(providedQuery, matchNames, query -> {
            if (query.occurance.equals(Occurance.SHOULD_OCCUR)) {
                orClauses.add(query);
            }
        });
    }

    /**
     * Examines {@code providedQuery} (and its nested boolean clauses), looking for should occur boolean clauses.
     * If it finds any then it starts collecting them as a new query and necessarily adds a must occur to
     * surround all the should occur clauses. This is a necessary step for the should occur clauses to be
     * evaluated correctly in the context of the complex query that gets built.
     *
     * @param providedQuery the input query object
     * @param handleShouldOccurMatch invoke lambda, passing in matching {@code Query} object, if match found
     */
    public static void findShouldOccurMatch(Query providedQuery,
            Consumer<Query> handleShouldOccurMatch) {
        if (providedQuery == null) {
            throw new IllegalArgumentException("'query' cannot be null");
        }
        if (providedQuery.booleanClauses != null) {
            // Sniff for all the should occur clauses; surround them with one must occur and return
            // them.
            boolean shouldOccurFlag = false;
            Query.Builder transformedQueryBuilder = Query.Builder.create();
            for (Query booleanClause : providedQuery.booleanClauses) {
                if (booleanClause.term != null
                        && booleanClause.occurance == Occurance.SHOULD_OCCUR) {
                    shouldOccurFlag = true;
                    transformedQueryBuilder.addClause(booleanClause);
                    // Default processing for the usual MUST OCCURs
                } else if (booleanClause.term != null
                        && (booleanClause.occurance == Occurance.MUST_OCCUR
                        || booleanClause.occurance == Occurance.MUST_NOT_OCCUR)) {
                    handleShouldOccurMatch.accept(booleanClause);
                }
                if (booleanClause.booleanClauses != null) {
                    findShouldOccurMatch(booleanClause, handleShouldOccurMatch);
                }
            }
            if (shouldOccurFlag) {
                Query returnQuery = transformedQueryBuilder.build();
                handleShouldOccurMatch.accept(returnQuery);
            }
        }
        if (providedQuery.booleanClauses == null || providedQuery.booleanClauses.isEmpty()) {
            return;
        }
    }

    /**
    *
    * Checks for matches in the given query against the given filter list and transforms the query to
    * use the version that is in persistence store vs the attribute name used in the presentation layer.
    * In addition tries to honor the SHOULD_OCCUR clauses as a separate class of their own.
    * Examines {@code providedQuery} (and its nested boolean clauses), looking for should occur boolean clauses.
    * If it finds any then it starts collecting them as a new query and necessarily adds a must occur to
    * surround all the should occur clauses. This is a necessary step for the should occur clauses to be
    * evaluated correctly in the context of the complex query that gets built.
    *
    * @param providedQuery the input query object
    * @param supportedFilterMap The map containing the supported fields for filtering.
    * @param caseInsensitiveFieldList The list that has the case insensitive fields for filtering.
    * @param supportedSpecialHandlingMap the set of fields for which special processing is done
    * @param handleQueryMatch invoke lambda, passing in matching {@code Query} object, if match found
    */
    public static void transformQuerySpec(Query providedQuery,
            Map<String, String> supportedFilterMap, List<String> caseInsensitiveFieldList,
            Map<String, Function<Query, Query>> supportedSpecialHandlingMap,
            Consumer<Query> handleQueryMatch) {
        if (providedQuery == null) {
            throw new IllegalArgumentException("'query' cannot be null");
        }
        if (providedQuery.booleanClauses != null) {
            // Sniff for all the should occur clauses; surround them with one must occur and return
            // them.
            boolean shouldOccurFlag = false;
            Query.Builder transformedQueryBuilder = Query.Builder.create();
            for (Query booleanClause : providedQuery.booleanClauses) {
                if (booleanClause.term != null
                        && booleanClause.occurance == Occurance.SHOULD_OCCUR
                        && supportedFilterMap.containsKey(booleanClause.term.propertyName)) {
                    shouldOccurFlag = true;
                    Query constructedQuery = constructQuery(booleanClause,
                            supportedFilterMap, caseInsensitiveFieldList,
                            supportedSpecialHandlingMap);
                    transformedQueryBuilder.addClause(constructedQuery);
                    // Default processing for the usual MUST OCCURs
                } else if (booleanClause.term != null
                        && booleanClause.occurance == Occurance.MUST_OCCUR
                        && supportedFilterMap.containsKey(booleanClause.term.propertyName)) {
                    Query constructedQuery = constructQuery(booleanClause,
                            supportedFilterMap, caseInsensitiveFieldList,
                            supportedSpecialHandlingMap);
                    handleQueryMatch.accept(constructedQuery);
                }
                if (booleanClause.booleanClauses != null) {
                    transformQuerySpec(booleanClause, supportedFilterMap,
                            caseInsensitiveFieldList, supportedSpecialHandlingMap,
                            handleQueryMatch);
                }
            }
            if (shouldOccurFlag) {
                Query returnQuery = transformedQueryBuilder.build();
                handleQueryMatch.accept(returnQuery);
            }
        }
        if (providedQuery.term != null
                && supportedFilterMap.containsKey(providedQuery.term.propertyName)) {
            Query constructedQuery = constructQuery(providedQuery,
                    supportedFilterMap, caseInsensitiveFieldList, supportedSpecialHandlingMap);
            handleQueryMatch.accept(constructedQuery);
        }
        if (providedQuery.booleanClauses == null || providedQuery.booleanClauses.isEmpty()) {
            return;
        }
    }

    /**
     * Constructs a query based on passed in query and the associated mapping. If a special handling map is passed
     * then the associated special handler is invoked to transform the query.
     */
    private static Query constructQuery(Query providedQuery, Map<String, String> supportedFilterMap,
            List<String> caseInsensitiveFieldList, Map<String, Function<Query, Query>> supportedSpecialHandlingMap) {
        if (supportedSpecialHandlingMap != null
                && supportedSpecialHandlingMap.keySet().contains(providedQuery.term.propertyName)) {
            return supportedSpecialHandlingMap.get(providedQuery.term.propertyName)
                    .apply(providedQuery);
        }
        Query constructedQuery = Query.Builder.create().build();
        String key = providedQuery.term.propertyName;
        constructedQuery.setTermPropertyName(supportedFilterMap.get(key));
        if (caseInsensitiveFieldList.contains(key)) {
            constructedQuery
                    .setCaseInsensitiveTermMatchValue(providedQuery.term.matchValue);
        } else {
            constructedQuery.setTermMatchValue(providedQuery.term.matchValue);
        }
        constructedQuery.setTermMatchType(providedQuery.term.matchType);
        constructedQuery.setOccurance(providedQuery.occurance);
        return constructedQuery;
    }

    /**
     * Inspects the passed in query specification and for every occurrence of should occur boolean clauses; encloses
     * them with a single must occur occurrence so that they are evaluated correctly in the context of the larger query that
     * is built.
     * @param querySpec
     */
    public static void transformOrClauses(QuerySpecification querySpec) {
        Query.Builder transformedORQueryBuilder = Query.Builder.create();
        FilterUtils.findShouldOccurMatch(querySpec.query, query -> {
            transformedORQueryBuilder.addClause(query);
        });
        Query transformedQuery = transformedORQueryBuilder.build();
        if (transformedQuery.booleanClauses != null
                && transformedQuery.booleanClauses.size() > 0) {
            querySpec.query = transformedQuery;
        }
    }

}