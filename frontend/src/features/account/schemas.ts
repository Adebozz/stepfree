import { z } from 'zod'

/** Mirrors the backend: 12 to 72 characters, and at most 72 bytes because of bcrypt. */
export const newPasswordSchema = z
  .string()
  .min(12, 'Use at least 12 characters')
  .max(72, 'Use at most 72 characters')
  .refine((value) => new TextEncoder().encode(value).length <= 72, 'This password is too long')
