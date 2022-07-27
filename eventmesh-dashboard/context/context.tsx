/*
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
 */

import {
  useMemo,
  useEffect,
  createContext,
  Dispatch,
} from 'react';
import { useImmerReducer } from 'use-immer';
import { State, Action } from './type';
import reducer from './reducer';

const initialState: State = {
  endpoint: 'http://localhost:10106',
};

const AppContext = createContext<{
  state: State;
  dispatch: Dispatch<Action>;
}>({
  state: initialState,
  dispatch: () => null,
});

interface AppProviderProps {
  children: React.ReactNode;
}

const AppProvider = ({ children }: AppProviderProps) => {
  const [state, dispatch] = useImmerReducer(
    reducer,
    initialState,
  );

  useEffect(() => {
    const localState = localStorage.getItem('state');
    if (localState === null) {
      return;
    }
    const parsedState: State = JSON.parse(localState);
    dispatch({
      type: 'SetState',
      payload: {
        endpoint: parsedState.endpoint,
      },
    });
  }, []);

  useEffect(() => {
    localStorage.setItem('state', JSON.stringify(state));
  }, [state]);

  const context = useMemo(() => ({
    state,
    dispatch,
  }), [state]);

  return (
    <AppContext.Provider
      value={context}
    >
      {children}
    </AppContext.Provider>
  );
};

export { AppContext, AppProvider };
