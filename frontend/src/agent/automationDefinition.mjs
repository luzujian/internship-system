// Missing model fields stay empty so the user/server must resolve them. Never
// invent a schedule or recipient just to make a preview render.
export function editableAutomationDefinition(value) {
  const object = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const definition = structuredClone(object(value))
  definition.trigger = { ...object(definition.trigger) }
  definition.repeatPolicy = { ...object(definition.repeatPolicy) }
  definition.notification = { ...object(definition.notification) }
  return definition
}
