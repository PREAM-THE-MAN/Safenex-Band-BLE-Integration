-- =========================================================================
-- SAFENEX Emergency Safety System - Supabase PostgreSQL Database & Storage Schema
-- =========================================================================

-- 1. Create or alter the emergency_events table
CREATE TABLE IF NOT EXISTS public.emergency_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    device_name TEXT NOT NULL DEFAULT 'SAFENEX-BAND',
    trigger_source TEXT NOT NULL,          -- 'BUTTON', 'SHAKE', 'UNKNOWN'
    latitude DOUBLE PRECISION,             -- GPS Latitude
    longitude DOUBLE PRECISION,            -- GPS Longitude
    accuracy REAL,                         -- GPS Accuracy in meters
    address TEXT,                          -- Resolved Street Address & Area
    maps_url TEXT,                         -- Direct Google Maps Coordinate Link
    audio_url TEXT,                        -- Direct Playable Audio Evidence Link in Supabase Storage
    photo_front_url TEXT,                  -- Front Camera Snapshot Link in Supabase Storage
    photo_back_url TEXT,                   -- Rear Camera Snapshot Link in Supabase Storage
    video_url TEXT,                        -- Continuous MP4 Video Evidence Link in Supabase Storage
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'VERIFYING', 'RESOLVED'
    guardians_notified INTEGER DEFAULT 0,  -- Count of SMS alerts dispatched
    resolved_at TIMESTAMPTZ                -- Timestamp when Safety PIN was verified
);

-- Ensure all columns are present if table already exists
ALTER TABLE public.emergency_events ADD COLUMN IF NOT EXISTS audio_url TEXT;
ALTER TABLE public.emergency_events ADD COLUMN IF NOT EXISTS photo_front_url TEXT;
ALTER TABLE public.emergency_events ADD COLUMN IF NOT EXISTS photo_back_url TEXT;
ALTER TABLE public.emergency_events ADD COLUMN IF NOT EXISTS video_url TEXT;

-- 2. Create emergency_gps_breadcrumbs table for continuous live real-time tracking
CREATE TABLE IF NOT EXISTS public.emergency_gps_breadcrumbs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES public.emergency_events(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL,
    timestamp TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.emergency_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_gps_breadcrumbs ENABLE ROW LEVEL SECURITY;

-- 4. Create Security Policies for Events Table
CREATE POLICY "Allow anonymous inserts" ON public.emergency_events FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anonymous reads" ON public.emergency_events FOR SELECT TO anon USING (true);
CREATE POLICY "Allow anonymous updates" ON public.emergency_events FOR UPDATE TO anon USING (true);

-- 5. Create Security Policies for Breadcrumbs Table
CREATE POLICY "Allow anon inserts on breadcrumbs" ON public.emergency_gps_breadcrumbs FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow anon reads on breadcrumbs" ON public.emergency_gps_breadcrumbs FOR SELECT TO anon USING (true);

-- 6. Create indexes for fast querying by time and status
CREATE INDEX IF NOT EXISTS idx_emergency_events_created_at ON public.emergency_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emergency_events_status ON public.emergency_events (status);
CREATE INDEX IF NOT EXISTS idx_breadcrumbs_event_id ON public.emergency_gps_breadcrumbs(event_id, timestamp ASC);

-- =========================================================================
-- Supabase Storage Buckets Setup: 'emergency-audio', 'emergency-photos', 'emergency-videos'
-- =========================================================================

-- 7. Create storage buckets (public for instant playback and viewing)
INSERT INTO storage.buckets (id, name, public) 
VALUES 
    ('emergency-audio', 'emergency-audio', true),
    ('emergency-photos', 'emergency-photos', true),
    ('emergency-videos', 'emergency-videos', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- 8. Storage Security Policies for emergency-audio
CREATE POLICY "Allow anon uploads to emergency-audio" 
ON storage.objects FOR INSERT TO anon WITH CHECK (bucket_id = 'emergency-audio');

CREATE POLICY "Allow anon reads from emergency-audio" 
ON storage.objects FOR SELECT TO anon USING (bucket_id = 'emergency-audio');

CREATE POLICY "Allow anon updates to emergency-audio" 
ON storage.objects FOR UPDATE TO anon USING (bucket_id = 'emergency-audio');

-- 9. Storage Security Policies for emergency-photos
CREATE POLICY "Allow anon uploads to emergency-photos" 
ON storage.objects FOR INSERT TO anon WITH CHECK (bucket_id = 'emergency-photos');

CREATE POLICY "Allow anon reads from emergency-photos" 
ON storage.objects FOR SELECT TO anon USING (bucket_id = 'emergency-photos');

CREATE POLICY "Allow anon updates to emergency-photos" 
ON storage.objects FOR UPDATE TO anon USING (bucket_id = 'emergency-photos');

-- 10. Storage Security Policies for emergency-videos
CREATE POLICY "Allow anon uploads to emergency-videos" 
ON storage.objects FOR INSERT TO anon WITH CHECK (bucket_id = 'emergency-videos');

CREATE POLICY "Allow anon reads from emergency-videos" 
ON storage.objects FOR SELECT TO anon USING (bucket_id = 'emergency-videos');

CREATE POLICY "Allow anon updates to emergency-videos" 
ON storage.objects FOR UPDATE TO anon USING (bucket_id = 'emergency-videos');
