import { useState, useEffect, useCallback } from 'react';
import { UserPersona } from '../types';
import { getStoredPersona, setStoredPersona } from '../services/api';
import { SEEDED_PERSONAS } from '../services/personas';

const PERSONA_CHANGE_EVENT = 'newtonite_persona_change';

export function usePersona() {
  const [activePersona, setActivePersonaState] = useState<UserPersona>(() => getStoredPersona());

  useEffect(() => {
    // Synchronize across components when persona changes
    const handlePersonaChange = (e: Event) => {
      const customEvent = e as CustomEvent<UserPersona>;
      if (customEvent.detail) {
        setActivePersonaState(customEvent.detail);
      } else {
        setActivePersonaState(getStoredPersona());
      }
    };

    window.addEventListener(PERSONA_CHANGE_EVENT, handlePersonaChange);
    return () => {
      window.removeEventListener(PERSONA_CHANGE_EVENT, handlePersonaChange);
    };
  }, []);

  const switchPersona = useCallback((personaId: string) => {
    const selected = SEEDED_PERSONAS.find((p) => p.id === personaId);
    if (!selected) {
      console.error(`[usePersona] Persona with ID ${personaId} not found.`);
      return;
    }

    setStoredPersona(selected);
    setActivePersonaState(selected);
    window.dispatchEvent(new CustomEvent(PERSONA_CHANGE_EVENT, { detail: selected }));
  }, []);

  return {
    activePersona,
    switchPersona,
    personas: SEEDED_PERSONAS,
  };
}
