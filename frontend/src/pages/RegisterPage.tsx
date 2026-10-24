import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'

import { safeNextPath, useCurrentUser, useRegister } from '../api/account'
import { FormError } from '../components/FormError'
import { TextField } from '../components/TextField'
import { newPasswordSchema } from '../features/account/schemas'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter your name').max(100, 'Use at most 100 characters'),
    email: z.email('Enter a valid email address').max(254),
    password: newPasswordSchema,
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })

type Values = z.infer<typeof schema>

export function RegisterPage() {
  useDocumentTitle('Create an account')
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const navigate = useNavigate()
  const user = useCurrentUser()
  const registerAccount = useRegister()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { fullName: '', email: '', password: '', confirmPassword: '' } })

  if (user.data) return <Navigate to={next} replace />

  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-2xl font-bold text-navy-900">Create an account</h1>
      <p className="mt-1 text-slate-700">Save journeys and, soon, get alerts when they're disrupted.</p>
      <form
        noValidate
        aria-label="Create an account"
        onSubmit={handleSubmit(({ fullName, email, password }) =>
          registerAccount.mutate({ fullName, email, password }, { onSuccess: () => void navigate(next, { replace: true }) }),
        )}
        className="mt-6 space-y-4 rounded-xl border border-navy-100 bg-white p-5 shadow-sm"
      >
        <FormError error={registerAccount.error} />
        <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters. A few random words work well."
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <button
          type="submit"
          disabled={registerAccount.isPending}
          className="w-full rounded-lg bg-brand px-5 py-2.5 font-semibold text-white shadow-sm hover:bg-brand-dark disabled:opacity-60"
        >
          {registerAccount.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="mt-4 text-sm text-slate-700">
        Already have an account?{' '}
        <Link to={`/sign-in${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-semibold text-brand underline">
          Sign in
        </Link>
      </p>
    </div>
  )
}
