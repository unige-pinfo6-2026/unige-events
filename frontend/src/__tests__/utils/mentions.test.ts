import { describe, expect, it } from 'vitest'
import { detectActiveMention, splitMentions } from '@/utils/mentions'

describe('detectActiveMention', () => {
  it('returns null when caret is at start of string', () => {
    expect(detectActiveMention('hello', 0)).toBeNull()
  })

  it('detects an active mention at the very start', () => {
    expect(detectActiveMention('@dan', 4)).toEqual({ atIndex: 0, prefix: 'dan' })
  })

  it('detects an active mention after a space', () => {
    expect(detectActiveMention('hello @al', 9)).toEqual({ atIndex: 6, prefix: 'al' })
  })

  it('lowercases the prefix', () => {
    expect(detectActiveMention('@DaNi', 5)).toEqual({ atIndex: 0, prefix: 'dani' })
  })

  it('does not trigger inside an email-like token', () => {
    expect(detectActiveMention('foo@example', 11)).toBeNull()
  })

  it('returns null when there is no @ to the left', () => {
    expect(detectActiveMention('hello', 5)).toBeNull()
  })

  it('returns empty prefix immediately after @', () => {
    expect(detectActiveMention('@', 1)).toEqual({ atIndex: 0, prefix: '' })
  })
})

describe('splitMentions', () => {
  it('returns an empty array for empty input', () => {
    expect(splitMentions('')).toEqual([])
  })

  it('returns a single text segment when there are no mentions', () => {
    expect(splitMentions('hello world')).toEqual([
      { kind: 'text', value: 'hello world' },
    ])
  })

  it('splits a single mention at the start', () => {
    expect(splitMentions('@daniel hi')).toEqual([
      { kind: 'mention', value: 'daniel' },
      { kind: 'text', value: ' hi' },
    ])
  })

  it('splits a single mention in the middle', () => {
    expect(splitMentions('hi @daniel, hello')).toEqual([
      { kind: 'text', value: 'hi ' },
      { kind: 'mention', value: 'daniel' },
      { kind: 'text', value: ', hello' },
    ])
  })

  it('splits multiple mentions', () => {
    expect(splitMentions('@a.bc and @def_g')).toEqual([
      { kind: 'mention', value: 'a.bc' },
      { kind: 'text', value: ' and ' },
      { kind: 'mention', value: 'def_g' },
    ])
  })

  it('preserves original casing of the handle', () => {
    expect(splitMentions('@Daniel')).toEqual([
      { kind: 'mention', value: 'Daniel' },
    ])
  })

  it('does NOT treat an email as a mention', () => {
    expect(splitMentions('foo@example.com')).toEqual([
      { kind: 'text', value: 'foo@example.com' },
    ])
  })

  it('ignores handles shorter than 3 characters (SCRUM-169 minimum)', () => {
    expect(splitMentions('hi @ab')).toEqual([
      { kind: 'text', value: 'hi @ab' },
    ])
  })

  it('caps handles at 30 characters', () => {
    const long = 'a'.repeat(35)
    const segments = splitMentions(`@${long}`)
    expect(segments[0]).toEqual({ kind: 'mention', value: 'a'.repeat(30) })
    expect(segments[1]).toEqual({ kind: 'text', value: 'a'.repeat(5) })
  })

  it('is callable repeatedly without stateful regex leakage', () => {
    // Defensive — guarding against accidental reuse of a shared regex
    // with the global flag. Run twice and assert the result is the same.
    expect(splitMentions('@daniel hi')).toEqual(splitMentions('@daniel hi'))
  })
})
