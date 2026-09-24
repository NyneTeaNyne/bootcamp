-- Module 11 (Spring Security): users can now log in with a password.
-- Existing users get a placeholder that is not a valid BCrypt hash, so they cannot log in until recreated.
ALTER TABLE users ADD COLUMN password VARCHAR NOT NULL DEFAULT 'no_password';
