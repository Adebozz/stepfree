import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'

import { useChangePassword, useCurrentUser, useDeleteAccount, useUpdateProfile } from '../api/account'
import type { User } from '../api/types'
import { FormError } from '../components/FormError'
import { TextField } from '../components/TextField'
import { newPasswordSchema } from '../features/account/schemas'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const card = 'rounded-xl border border-navy-100 bg-white p-5 shadow-sm'
const primaryButton = 'rounded-lg bg-brand px-4 py-2 font-semibold text-white shadow-sm hover:bg-brand-dark disabled:opacity-60'

/** Rendered inside RequireAuth, so the user is known. */
export function AccountPage() {
  useDocumentTitle('Your account')
  const user = useCurrentUser().data
  if (!user) return null
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-navy-900">Your account</h1>
      <ProfileForm user={user} />
      <PasswordForm />
      <DeleteAccount />
    </div>
  )
}

const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(100, 'Use at most 100 characters'),
  email: z.email('Enter a valid email address').max(254),
  currentPassword: z.string(),
})

function ProfileForm({ user }: { user: User }) {
  const update = useUpdateProfile()
  const {
    register,
    handleSubmit,
    control,
    setError,
    resetField,
    formState: { errors },
  } = useForm({ resolver: zodResolver(profileSchema), defaultValues: { fullName: user.fullName, email: user.email, currentPassword: '' } })
  const email = useWatch({ control, name: 'email' })
  const emailChanged = email.trim().toLowerCase() !== user.email

  return (
    <section aria-labelledby="profile-heading" className={card}>
      <h2 id="profile-heading" className="text-lg font-semibold text-navy-900">
        Profile
      </h2>
      <form
        noValidate
        aria-labelledby="profile-heading"
        className="mt-3 space-y-4"
        onSubmit={handleSubmit((values) => {
          if (emailChanged && !values.currentPassword) {
            setError('currentPassword', { message: 'Enter your current password to change your email' })
            return
          }
          update.mutate(
            emailChanged
              ? { fullName: values.fullName, email: values.email, currentPassword: values.currentPassword }
              : { fullName: values.fullName },
            { onSuccess: () => resetField('currentPassword') },
          )
        })}
      >
        <FormError error={update.error} />
        {update.isSuccess && (
          <p role="status" className="text-sm font-medium text-green-800">
            Profile saved.
          </p>
        )}
        <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        {emailChanged && (
          <TextField
            label="Current password"
            type="password"
            autoComplete="current-password"
            hint="Needed to change your email."
            error={errors.currentPassword?.message}
            {...register('currentPassword')}
          />
        )}
        <button type="submit" disabled={update.isPending} className={primaryButton}>
          Save profile
        </button>
      </form>
    </section>
  )
}

const passwordSchema = z
  .object({ currentPassword: z.string().min(1, 'Enter your current password'), newPassword: newPasswordSchema, confirmPassword: z.string() })
  .refine((values) => values.newPassword === values.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })

function PasswordForm() {
  const change = useChangePassword()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ resolver: zodResolver(passwordSchema), defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' } })

  return (
    <section aria-labelledby="password-heading" className={card}>
      <h2 id="password-heading" className="text-lg font-semibold text-navy-900">
        Change password
      </h2>
      <p className="mt-1 text-sm text-slate-600">You'll stay signed in here and be signed out everywhere else.</p>
      <form
        noValidate
        aria-labelledby="password-heading"
        className="mt-3 space-y-4"
        onSubmit={handleSubmit(({ currentPassword, newPassword }) =>
          change.mutate({ currentPassword, newPassword }, { onSuccess: () => reset() }),
        )}
      >
        <FormError error={change.error} />
        {change.isSuccess && (
          <p role="status" className="text-sm font-medium text-green-800">
            Password changed.
          </p>
        )}
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters."
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <button type="submit" disabled={change.isPending} className={primaryButton}>
          Change password
        </button>
      </form>
    </section>
  )
}

const deleteSchema = z.object({
  password: z.string().min(1, 'Enter your password'),
  confirm: z.boolean().refine((checked) => checked, 'Tick the box to confirm'),
})

function DeleteAccount() {
  const remove = useDeleteAccount()
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(deleteSchema), defaultValues: { password: '', confirm: false } })

  return (
    <section aria-labelledby="delete-heading" className="rounded-xl border border-red-200 bg-white p-5 shadow-sm">
      <h2 id="delete-heading" className="text-lg font-semibold text-red-900">
        Delete account
      </h2>
      <p className="mt-1 text-sm text-slate-700">
        This permanently deletes your account and all your saved journeys, and signs you out on every device. It can't be undone.
      </p>
      <form
        noValidate
        aria-labelledby="delete-heading"
        className="mt-3 space-y-4"
        onSubmit={handleSubmit(({ password }) => remove.mutate(password, { onSuccess: () => void navigate('/', { replace: true }) }))}
      >
        <FormError error={remove.error} />
        <TextField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
        <div>
          <label className="flex items-start gap-2 text-sm text-navy-900">
            <input
              type="checkbox"
              className="mt-0.5 size-4"
              aria-invalid={errors.confirm ? true : undefined}
              aria-describedby={errors.confirm ? 'confirm-delete-error' : undefined}
              {...register('confirm')}
            />
            I understand my account and saved journeys will be deleted permanently.
          </label>
          {errors.confirm && (
            <p id="confirm-delete-error" className="mt-1 text-sm font-medium text-red-700">
              {errors.confirm.message}
            </p>
          )}
        </div>
        <button
          type="submit"
          disabled={remove.isPending}
          className="rounded-lg bg-red-700 px-4 py-2 font-semibold text-white hover:bg-red-800 disabled:opacity-60"
        >
          Delete my account
        </button>
      </form>
    </section>
  )
}
