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
import java.util.concurrent.atomic.AtomicReference;

public class TopicParser {
    private static final String TOPIC_DELIMITER = "\\.";

    private final TopicWordDictionary _dictionary = new TopicWordDictionary();
    private final AtomicReference<TopicMatcherDFAState> _stateMachine = new AtomicReference<>();

    private static class Position {
        private final TopicWord _word;
        private final boolean _selfTransition;
        private final int _position;
        private final boolean _endState;
        private boolean _followedByAnyLoop;

        private Position(final int position, final TopicWord word, final boolean selfTransition,
            final boolean endState) {
            _position = position;
            _word = word;
            _selfTransition = selfTransition;
            _endState = endState;
        }

        private TopicWord getWord() {
            return _word;
        }

        private boolean isSelfTransition() {
            return _selfTransition;
        }

        private int getPosition() {
            return _position;
        }

        private boolean isEndState() {
            return _endState;
        }

        private boolean isFollowedByAnyLoop() {
            return _followedByAnyLoop;
        }

        private void setFollowedByAnyLoop(boolean followedByAnyLoop) {
            _followedByAnyLoop = followedByAnyLoop;
        }
    }

    private static final Position ERROR_POSITION = new Position(Integer.MAX_VALUE, null, true, false);

    private static class SimpleState {
        private Set<Position> _positions;
        private Map<TopicWord, SimpleState> _nextState;
    }

    public void addBinding(String bindingKey, TopicMatcherResult result) {

        TopicMatcherDFAState startingStateMachine;
        TopicMatcherDFAState newStateMachine;

        do {
            startingStateMachine = _stateMachine.get();
            if (startingStateMachine == null) {
                newStateMachine = createStateMachine(bindingKey, result);
            } else {
                newStateMachine = startingStateMachine.mergeStateMachines(createStateMachine(bindingKey, result));
            }

        }
        while (!_stateMachine.compareAndSet(startingStateMachine, newStateMachine));

    }

    public Collection<TopicMatcherResult> parse(String routingKey) {
        TopicMatcherDFAState stateMachine = _stateMachine.get();
        if (stateMachine == null) {
            return Collections.emptySet();
        } else {
            return stateMachine.parse(_dictionary, routingKey);
        }
    }

    private TopicMatcherDFAState createStateMachine(String bindingKey, TopicMatcherResult result) {
        List<TopicWord> wordList = createTopicWordList(bindingKey);
        int wildCards = 0;
        for (TopicWord word : wordList) {
            if (word == TopicWord.WILDCARD_WORD) {
                wildCards++;
            }
        }
        if (wildCards == 0) {
            TopicMatcherDFAState[] states = new TopicMatcherDFAState[wordList.size() + 1];
            states[states.length - 1] = new TopicMatcherDFAState(Collections.emptyMap(), Collections.singleton(result));
            for (int i = states.length - 2; i >= 0; i--) {
                states[i] = new TopicMatcherDFAState(Collections.singletonMap(wordList.get(i), states[i + 1]), Collections.emptySet());

            }
            return states[0];
        } else if (wildCards == wordList.size()) {
            Map<TopicWord, TopicMatcherDFAState> stateMap = new HashMap<>();
            TopicMatcherDFAState state = new TopicMatcherDFAState(stateMap, Collections.singleton(result));
            stateMap.put(TopicWord.ANY_WORD, state);
            return state;
        }

        int positionCount = wordList.size() - wildCards;

        Position[] positions = new Position[positionCount + 1];

        int lastWord;

        if (wordList.get(wordList.size() - 1) == TopicWord.WILDCARD_WORD) {
            lastWord = wordList.size() - 1;
            positions[positionCount] = new Position(positionCount, TopicWord.ANY_WORD, true, true);
        } else {
            lastWord = wordList.size();
            positions[positionCount] = new Position(positionCount, TopicWord.ANY_WORD, false, true);
        }

        int pos = 0;
        int wordPos = 0;

        while (wordPos < lastWord) {
            TopicWord word = wordList.get(wordPos++);

            if (word == TopicWord.WILDCARD_WORD) {
                int nextWordPos = wordPos++;
                word = wordList.get(nextWordPos);

                positions[pos] = new Position(pos++, word, true, false);
            } else {
                positions[pos] = new Position(pos++, word, false, false);
            }

        }

        for (int p = 0; p < positionCount; p++) {
            boolean followedByWildcards = true;

            int n = p;
            while (followedByWildcards && n < (positionCount + 1)) {

                if (positions[n].isSelfTransition()) {
                    break;
                } else if (positions[n].getWord() != TopicWord.ANY_WORD) {
                    followedByWildcards = false;
                }
                n++;
            }

            positions[p].setFollowedByAnyLoop(followedByWildcards && (n != positionCount + 1));
        }

        // from each position you transition to a set of other positions.
        // we approach this by examining steps of increasing length - so we
        // look how far we can go from the start position in 1 word, 2 words, etc...

        Map<Set<Position>, SimpleState> stateMap = new HashMap<>();

        SimpleState state = new SimpleState();
        state._positions = Collections.singleton(positions[0]);
        stateMap.put(state._positions, state);

        calculateNextStates(state, stateMap, positions);

        SimpleState[] simpleStates = stateMap.values().toArray(new SimpleState[stateMap.size()]);
        HashMap<TopicWord, TopicMatcherDFAState>[] dfaStateMaps = new HashMap[simpleStates.length];
        Map<SimpleState, TopicMatcherDFAState> simple2DFAMap = new HashMap<>();

        for (int i = 0; i < simpleStates.length; i++) {

            Collection<TopicMatcherResult> results;
            boolean endState = false;

            for (Position p : simpleStates[i]._positions) {
                if (p.isEndState()) {
                    endState = true;
                    break;
                }
            }

            if (endState) {
                results = Collections.singleton(result);
            } else {
                results = Collections.emptySet();
            }

            dfaStateMaps[i] = new HashMap<>();
            simple2DFAMap.put(simpleStates[i], new TopicMatcherDFAState(dfaStateMaps[i], results));

        }
        for (int i = 0; i < simpleStates.length; i++) {
            SimpleState simpleState = simpleStates[i];

            Map<TopicWord, SimpleState> nextSimpleStateMap = simpleState._nextState;
            for (Map.Entry<TopicWord, SimpleState> stateMapEntry : nextSimpleStateMap.entrySet()) {
                dfaStateMaps[i].put(stateMapEntry.getKey(), simple2DFAMap.get(stateMapEntry.getValue()));
            }

        }

        return simple2DFAMap.get(state);

    }

    private void calculateNextStates(final SimpleState state,
        final Map<Set<Position>, SimpleState> stateMap,
        final Position[] positions) {
        Map<TopicWord, Set<Position>> transitions = new HashMap<>();

        for (Position pos : state._positions) {
            if (pos.isSelfTransition()) {
                Set<Position> dest = transitions.get(TopicWord.ANY_WORD);
                if (dest == null) {
                    dest = new HashSet<>();
                    transitions.put(TopicWord.ANY_WORD, dest);
                }
                dest.add(pos);
            }

            final int nextPos = pos.getPosition() + 1;
            Position nextPosition = nextPos == positions.length ? ERROR_POSITION : positions[nextPos];

            Set<Position> dest = transitions.get(pos.getWord());
            if (dest == null) {
                dest = new HashSet<>();
                transitions.put(pos.getWord(), dest);
            }
            dest.add(nextPosition);

        }

        Set<Position> anyWordTransitions = transitions.get(TopicWord.ANY_WORD);
        if (anyWordTransitions != null) {
            for (Set<Position> dest : transitions.values()) {
                dest.addAll(anyWordTransitions);
            }
        }

        state._nextState = new HashMap<>();

        for (Map.Entry<TopicWord, Set<Position>> dest : transitions.entrySet()) {

            if (dest.getValue().size() > 1) {
                dest.getValue().remove(ERROR_POSITION);
            }
            Position loopingTerminal = null;
            for (Position destPos : dest.getValue()) {
                if (destPos.isSelfTransition() && destPos.isEndState()) {
                    loopingTerminal = destPos;
                    break;
                }
            }

            if (loopingTerminal != null) {
                dest.setValue(Collections.singleton(loopingTerminal));
            } else {
                Position anyLoop = null;
                for (Position destPos : dest.getValue()) {
                    if (destPos.isFollowedByAnyLoop()) {
                        if (anyLoop == null || anyLoop.getPosition() < destPos.getPosition()) {
                            anyLoop = destPos;
                        }
                    }
                }
                if (anyLoop != null) {
                    Collection<Position> removals = new ArrayList<>();
                    for (Position destPos : dest.getValue()) {
                        if (destPos.getPosition() < anyLoop.getPosition()) {
                            removals.add(destPos);
                        }
                    }
                    dest.getValue().removeAll(removals);
                }
            }

            SimpleState stateForEntry = stateMap.get(dest.getValue());
            if (stateForEntry == null) {
                stateForEntry = new SimpleState();
                stateForEntry._positions = dest.getValue();
                stateMap.put(dest.getValue(), stateForEntry);
                calculateNextStates(stateForEntry,
                    stateMap,
                    positions);
            }
            state._nextState.put(dest.getKey(), stateForEntry);

        }

        // remove redundant transitions
        SimpleState anyWordState = state._nextState.get(TopicWord.ANY_WORD);
        if (anyWordState != null) {
            List<TopicWord> removeList = new ArrayList<>();
            for (Map.Entry<TopicWord, SimpleState> entry : state._nextState.entrySet()) {
                if (entry.getValue() == anyWordState && entry.getKey() != TopicWord.ANY_WORD) {
                    removeList.add(entry.getKey());
                }
            }
            for (TopicWord removeKey : removeList) {
                state._nextState.remove(removeKey);
            }
        }

    }

    private List<TopicWord> createTopicWordList(final String bindingKey) {
        String[] tokens = bindingKey.split(TOPIC_DELIMITER);
        TopicWord previousWord = null;

        List<TopicWord> wordList = new ArrayList<>();

        for (String token : tokens) {
            TopicWord nextWord = _dictionary.getOrCreateWord(token);
            if (previousWord == TopicWord.WILDCARD_WORD) {

                if (nextWord == TopicWord.WILDCARD_WORD) {
                    // consecutive wildcards can be merged
                    // i.e. subsequent wildcards can be discarded
                    continue;
                } else if (nextWord == TopicWord.ANY_WORD) {
                    // wildcard and anyword can be reordered to always put anyword first
                    wordList.set(wordList.size() - 1, TopicWord.ANY_WORD);
                    nextWord = TopicWord.WILDCARD_WORD;
                }
            }
            wordList.add(nextWord);
            previousWord = nextWord;

        }
        return wordList;
    }

}
