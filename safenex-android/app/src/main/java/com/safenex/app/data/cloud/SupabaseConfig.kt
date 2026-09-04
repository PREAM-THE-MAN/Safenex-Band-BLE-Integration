package com.safenex.app.data.cloud

/**
 * Global Configuration for Supabase Backend Database.
 */
object SupabaseConfig {
    var SUPABASE_URL: String = "https://vrxyxwzvcinytsorauau.supabase.co"
    var SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZyeHl4d3p2Y2lueXRzb3JhdWF1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc5ODE4MDMsImV4cCI6MjEwMzU1NzgwM30.r0CbtU3H1Spx2CNQm5P3HWvhuzJApQG_rvFotq28f7o"

    /**
     * Checks whether valid Supabase credentials have been configured.
     */
    fun isConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() &&
                !SUPABASE_URL.contains("your-project") &&
                SUPABASE_ANON_KEY.isNotBlank() &&
                !SUPABASE_ANON_KEY.contains("your-anon-key")
    }
}
