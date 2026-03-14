export default {
  getDepartments() {
    return Promise.resolve({ data: [] })
  },
  batchDeleteDepartments(ids) {
    return Promise.resolve({ code: 200 })
  }
}
