-- Add nickname column to users table
ALTER TABLE users ADD COLUMN nickname VARCHAR(64) NULL AFTER username;
