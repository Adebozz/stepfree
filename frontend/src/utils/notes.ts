/** TfL stores accessibility notes in a 255-character field and silently cuts longer text. */
const TFL_NOTE_LIMIT = 255

/** True if a TfL note looks cut off: at the field limit and not ending a sentence. */
export function isCutOffByTfl(note: string): boolean {
  return note.length >= TFL_NOTE_LIMIT && !/[.!?)]\s*$/.test(note)
}
