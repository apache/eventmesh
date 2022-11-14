/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.eventmesh.runtime.core.protocol.amqp.exchange.topic;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TopicMatcherDFAState {
    private static final AtomicInteger stateId = new AtomicInteger();

    private final int _id = stateId.incrementAndGet();

    private final Collection<TopicMatcherResult> _results;
    private final Map<TopicWord, TopicMatcherDFAState> _nextStateMap;
    private static final String TOPIC_DELIMITER = "\\.";

    public TopicMatcherDFAState(Map<TopicWord, TopicMatcherDFAState> nextStateMap,
                                Collection<TopicMatcherResult> results) {
        _nextStateMap = nextStateMap;
        _results = results;
    }

    public TopicMatcherDFAState nextState(TopicWord word) {
        final TopicMatcherDFAState nextState = _nextStateMap.get(word);
        return nextState == null ? _nextStateMap.get(TopicWord.ANY_WORD) : nextState;
    }

    public Collection<TopicMatcherResult> terminate() {
        return _results;
    }

    public Collection<TopicMatcherResult> parse(TopicWordDictionary dictionary, String routingKey) {
        return parse(dictionary, Arrays.asList(routingKey.split(TOPIC_DELIMITER)).iterator());
    }

    private Collection<TopicMatcherResult> parse(final TopicWordDictionary dictionary,
                                                 final Iterator<String> tokens) {
        if (!tokens.hasNext()) {
            return _results;
        }
        TopicWord word = dictionary.getWord(tokens.next());
        TopicMatcherDFAState nextState = _nextStateMap.get(word);
        if (nextState == null && word != TopicWord.ANY_WORD) {
            nextState = _nextStateMap.get(TopicWord.ANY_WORD);
        }
        if (nextState == null) {
            return Collections.EMPTY_LIST;
        }
        // Shortcut if we are at a looping terminal state
        if ((nextState == this) && (_nextStateMap.size() == 1) && _nextStateMap.containsKey(TopicWord.ANY_WORD)) {
            return _results;
        }

        return nextState.parse(dictionary, tokens);

    }

    public TopicMatcherDFAState mergeStateMachines(TopicMatcherDFAState otherStateMachine) {
        Map<Set<TopicMatcherDFAState>, TopicMatcherDFAState> newStateMap = new HashMap<Set<TopicMatcherDFAState>, TopicMatcherDFAState>();

        Collection<TopicMatcherResult> results;

        if (_results.isEmpty()) {
            results = otherStateMachine._results;
        } else if (otherStateMachine._results.isEmpty()) {
            results = _results;
        } else {
            results = new HashSet<TopicMatcherResult>(_results);
            results.addAll(otherStateMachine._results);
        }

        final Map<TopicWord, TopicMatcherDFAState> newNextStateMap = new HashMap<TopicWord, TopicMatcherDFAState>();

        TopicMatcherDFAState newState = new TopicMatcherDFAState(newNextStateMap, results);

        Set<TopicMatcherDFAState> oldStates = new HashSet<TopicMatcherDFAState>();
        oldStates.add(this);
        oldStates.add(otherStateMachine);

        newStateMap.put(oldStates, newState);

        mergeStateMachines(oldStates, newNextStateMap, newStateMap);

        return newState;

    }

    private static void mergeStateMachines(
            final Set<TopicMatcherDFAState> oldStates,
            final Map<TopicWord, TopicMatcherDFAState> newNextStateMap,
            final Map<Set<TopicMatcherDFAState>, TopicMatcherDFAState> newStateMap) {
        Map<TopicWord, Set<TopicMatcherDFAState>> nfaMap = new HashMap<TopicWord, Set<TopicMatcherDFAState>>();

        for (TopicMatcherDFAState state : oldStates) {
            Map<TopicWord, TopicMatcherDFAState> map = state._nextStateMap;
            for (Map.Entry<TopicWord, TopicMatcherDFAState> entry : map.entrySet()) {
                Set<TopicMatcherDFAState> states = nfaMap.get(entry.getKey());
                if (states == null) {
                    states = new HashSet<TopicMatcherDFAState>();
                    nfaMap.put(entry.getKey(), states);
                }
                states.add(entry.getValue());
            }
        }

        Set<TopicMatcherDFAState> anyWordStates = nfaMap.get(TopicWord.ANY_WORD);

        for (Map.Entry<TopicWord, Set<TopicMatcherDFAState>> transition : nfaMap.entrySet()) {
            Set<TopicMatcherDFAState> destinations = transition.getValue();

            if (anyWordStates != null) {
                destinations.addAll(anyWordStates);
            }

            TopicMatcherDFAState nextState = newStateMap.get(destinations);
            if (nextState == null) {

                if (destinations.size() == 1) {
                    nextState = destinations.iterator().next();
                    newStateMap.put(destinations, nextState);
                } else {
                    Collection<TopicMatcherResult> results;

                    Set<Collection<TopicMatcherResult>> resultSets = new HashSet<Collection<TopicMatcherResult>>();
                    for (TopicMatcherDFAState destination : destinations) {
                        resultSets.add(destination._results);
                    }
                    resultSets.remove(Collections.EMPTY_SET);
                    if (resultSets.size() == 0) {
                        results = Collections.EMPTY_SET;
                    } else if (resultSets.size() == 1) {
                        results = resultSets.iterator().next();
                    } else {
                        results = new HashSet<TopicMatcherResult>();
                        for (Collection<TopicMatcherResult> oldResult : resultSets) {
                            results.addAll(oldResult);
                        }
                    }

                    final Map<TopicWord, TopicMatcherDFAState> nextStateMap = new HashMap<TopicWord, TopicMatcherDFAState>();

                    nextState = new TopicMatcherDFAState(nextStateMap, results);
                    newStateMap.put(destinations, nextState);

                    mergeStateMachines(
                            destinations,
                            nextStateMap,
                            newStateMap);

                }

            }
            newNextStateMap.put(transition.getKey(), nextState);
        }

        // Remove redundant transitions where defined tokenWord has same action as ANY_WORD
        TopicMatcherDFAState anyWordState = newNextStateMap.get(TopicWord.ANY_WORD);
        if (anyWordState != null) {
            List<TopicWord> removeList = new ArrayList<TopicWord>();
            for (Map.Entry<TopicWord, TopicMatcherDFAState> entry : newNextStateMap.entrySet()) {
                if (entry.getValue() == anyWordState && entry.getKey() != TopicWord.ANY_WORD) {
                    removeList.add(entry.getKey());
                }
            }
            for (TopicWord removeKey : removeList) {
                newNextStateMap.remove(removeKey);
            }
        }

    }

    @Override
    public String toString() {
        StringBuilder transitions = new StringBuilder();
        for (Map.Entry<TopicWord, TopicMatcherDFAState> entry : _nextStateMap.entrySet()) {
            transitions.append("[ ");
            transitions.append(entry.getKey());
            transitions.append("\t ->\t ");
            transitions.append(entry.getValue().getId());
            transitions.append(" ]\n");
        }

        return "[ State " + getId() + " ]\n" + transitions + "\n";

    }

    public String reachableStates() {
        StringBuilder result = new StringBuilder("Start state: " + getId() + "\n");

        SortedSet<TopicMatcherDFAState> reachableStates =
                new TreeSet<TopicMatcherDFAState>(new Comparator<TopicMatcherDFAState>() {
                    @Override
                    public int compare(final TopicMatcherDFAState o1, final TopicMatcherDFAState o2) {
                        return o1.getId() - o2.getId();
                    }
                });
        reachableStates.add(this);

        int count;

        do {
            count = reachableStates.size();
            Collection<TopicMatcherDFAState> originalStates = new ArrayList<TopicMatcherDFAState>(reachableStates);
            for (TopicMatcherDFAState state : originalStates) {
                reachableStates.addAll(state._nextStateMap.values());
            }
        }
        while (reachableStates.size() != count);

        for (TopicMatcherDFAState state : reachableStates) {
            result.append(state.toString());
        }

        return result.toString();
    }

    int getId() {
        return _id;
    }
}
