export default {
  getStudents() {
    return Promise.resolve({ data: [] })
  },
  batchDeleteStudents(ids) {
    return Promise.resolve({ code: 200 })
  }
}
