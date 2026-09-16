package com.akshaya.outlast.model;

public enum StatType {
    /**
     * 100 = Extremely loyal/honest.
     * 1   = Prone to lies/betrayal.
     */
    TRUSTWORTHINESS,

    /**
     * 100 = Calm and cool under pressure.
     * 1   = Volatile, panics easily.
     */
    EMOTIONAL_STABILITY,

    /**
     * 100 = Selfless team player.
     * 1   = Selfish, keeps resources for themselves.
     */
    COOPERATION,

    /**
     * 100 = Highly empathetic and socially aware.
     * 1   = Deceptive and highly manipulative.
     */
    SOCIAL_INTELLIGENCE,

    /**
     * 100 = Physically resilient, handles cold/hunger well.
     * 1   = Physically fragile, easily exhausted.
     */
    SURVIVAL_GRIT,

    /**
     * 100 = Dominant leader, takes control.
     * 1   = Passive follower, blends in.
     */
    ASSERTIVENESS
}
