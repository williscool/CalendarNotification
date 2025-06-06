import React, { createContext, useContext } from 'react';
import { SupabaseClient } from '@supabase/supabase-js';

interface SupabaseContextType {
  supabaseClient: SupabaseClient | null;
}

const SupabaseContext = createContext<SupabaseContextType>({ supabaseClient: null });

export const useSupabase = () => {
  const context = useContext(SupabaseContext);
  if (!context) {
    throw new Error('useSupabase must be used within a SupabaseProvider');
  }
  return context;
};

interface SupabaseProviderProps {
  children: React.ReactNode;
  client: SupabaseClient | null;
}

export const SupabaseProvider: React.FC<SupabaseProviderProps> = ({ children, client }) => {
  return (
    <SupabaseContext.Provider value={{ supabaseClient: client }}>
      {children}
    </SupabaseContext.Provider>
  );
}; 