import { SUBMIT_FORMS } from './formCoverage'

/** Keep route capabilities attached to the same component and existing form registry, including after lazy loading. */
export function agentPage(file: string, load: () => Promise<any>) {
  const agentForms = SUBMIT_FORMS.filter(form => form.file === file).map(form => form.title)
  return Object.assign(async () => {
    const module = await load()
    Object.assign(module.default, { agentForms })
    return module
  }, { agentForms })
}
