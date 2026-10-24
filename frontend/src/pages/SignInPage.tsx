import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'

import { safeNextPath, useCurrentUser, useSignIn } from '../api/account'
import { FormError } from '../components/FormError'
import { TextField } from '../components/TextField'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email address'),
  password: z.string().min(1, 'Enter your password'),
})

type Values = z.infer<typeof schema>

export function SignInPage() {
  useDocumentTitle('Sign in')
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const navigate = useNavigate()
  const user = useCurrentUser()
  const signIn = useSignIn()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  if (user.data) return <Navigate to={next} replace />

  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-2xl font-bold text-navy-900">Sign in</h1>
      <p className="mt-1 text-slate-700">Sign in to see and save your journeys.</p>
      <form
        noValidate
        aria-label="Sign in"
        onSubmit={handleSubmit((values) => signIn.mutate(values, { onSuccess: () => void navigate(next, { replace: true }) }))}
        className="mt-6 space-y-4 rounded-xl border border-navy-100 bg-white p-5 shadow-sm"
      >
        <FormError error={signIn.error} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <button
          type="submit"
          disabled={signIn.isPending}
          className="w-full rounded-lg bg-brand px-5 py-2.5 font-semibold text-white shadow-sm hover:bg-brand-dark disabled:opacity-60"
        >
          {signIn.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="mt-4 text-sm text-slate-700">
        New to StepFree London?{' '}
        <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-semibold text-brand underline">
          Create an account
        </Link>
      </p>
    </div>
  )
}
