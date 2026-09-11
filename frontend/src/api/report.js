import request from '../utils/request'

export function getReportSummary(params) {
  return request.get('/report/summary', { params })
}

export function exportReport(params) {
  return request.get('/report/export', { params, responseType: 'blob' })
}
